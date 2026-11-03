package com.realdeelstudios.mysticwilderness.world

import com.github.quillraven.fleks.Entity
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.ecs.components.Direction
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Chunk-bucketed spatial partition for resource nodes.
 *
 * Provides:
 * 1. O(1) tile solidity query for blocking nodes (`isTileBlocked(tx, ty)`).
 * 2. Allocation-free targeting queries within a directional cone.
 */
class NodeSpatialIndex(
    val widthInTiles: Int = 96,
    val heightInTiles: Int = 96,
    val chunkSize: Int = GameConfig.CHUNK_SIZE
) {
    val chunksAcrossX: Int = (widthInTiles + chunkSize - 1) / chunkSize
    val chunksAcrossY: Int = (heightInTiles + chunkSize - 1) / chunkSize
    private val totalChunks = chunksAcrossX * chunksAcrossY

    // Buckets of entities per chunk
    private val chunkBuckets: Array<ArrayList<Entity>> = Array(totalChunks) { ArrayList() }

    // Fast O(1) blocked tile mask for collision
    private val blockedTileGrid = BooleanArray(widthInTiles * heightInTiles)

    // Map each entity to its occupied solid tiles so they can be toggled cleanly
    private val entityBlockedTiles = HashMap<Int, ArrayList<Int>>()

    fun clear() {
        for (i in 0 until totalChunks) {
            chunkBuckets[i].clear()
        }
        blockedTileGrid.fill(false)
        entityBlockedTiles.clear()
    }

    private fun getChunkIndex(cx: Int, cy: Int): Int = cy * chunksAcrossX + cx

    fun registerNode(entity: Entity, tileX: Int, tileY: Int, blocksMovement: Boolean, tileSpanX: Int = 1, tileSpanY: Int = 1) {
        val cx = (tileX / chunkSize).coerceIn(0, chunksAcrossX - 1)
        val cy = (tileY / chunkSize).coerceIn(0, chunksAcrossY - 1)
        val bucketIdx = getChunkIndex(cx, cy)
        chunkBuckets[bucketIdx].add(entity)

        if (blocksMovement) {
            setNodeBlocking(entity, tileX, tileY, tileSpanX, tileSpanY, true)
        }
    }

    fun setNodeBlocking(entity: Entity, tileX: Int, tileY: Int, tileSpanX: Int = 1, tileSpanY: Int = 1, isBlocking: Boolean) {
        val entityId = entity.id
        if (isBlocking) {
            val tiles = entityBlockedTiles.getOrPut(entityId) { ArrayList(tileSpanX * tileSpanY) }
            tiles.clear()
            for (dy in 0 until tileSpanY) {
                for (dx in 0 until tileSpanX) {
                    val tx = tileX + dx
                    val ty = tileY + dy
                    if (tx in 0 until widthInTiles && ty in 0 until heightInTiles) {
                        val idx = ty * widthInTiles + tx
                        blockedTileGrid[idx] = true
                        tiles.add(idx)
                    }
                }
            }
        } else {
            val tiles = entityBlockedTiles[entityId]
            if (tiles != null) {
                for (i in 0 until tiles.size) {
                    blockedTileGrid[tiles[i]] = false
                }
            }
        }
    }

    /**
     * O(1) query used to extend GameMap.isTileSolid without a secondary collision path.
     */
    fun isTileBlocked(tileX: Int, tileY: Int): Boolean {
        if (tileX !in 0 until widthInTiles || tileY !in 0 until heightInTiles) return false
        return blockedTileGrid[tileY * widthInTiles + tileX]
    }

    /**
     * Finds the nearest targetable node within 1.25 tiles in a 120-degree cone.
     * Zero-allocation execution.
     */
    fun findTarget(
        playerCenterX: Float,
        playerCenterY: Float,
        facing: Direction,
        positionLookup: (Entity) -> Position,
        nodeLookup: (Entity) -> ResourceNodeComponent
    ): Entity? {
        val maxDistPx = 1.25f * GameConfig.TILE_SIZE // 20.0 px
        val maxDistSq = maxDistPx * maxDistPx

        // Unit vector for facing direction
        val dirX: Float
        val dirY: Float
        when (facing) {
            Direction.UP -> { dirX = 0f; dirY = 1f }
            Direction.DOWN -> { dirX = 0f; dirY = -1f }
            Direction.LEFT -> { dirX = -1f; dirY = 0f }
            Direction.RIGHT -> { dirX = 1f; dirY = 0f }
        }

        // 120-degree cone means half-angle is 60 degrees. cos(60 deg) = 0.5f.
        val minCos = 0.5f

        val minTileX = floor((playerCenterX - maxDistPx) / (chunkSize * GameConfig.TILE_SIZE)).toInt().coerceIn(0, chunksAcrossX - 1)
        val maxTileX = floor((playerCenterX + maxDistPx) / (chunkSize * GameConfig.TILE_SIZE)).toInt().coerceIn(0, chunksAcrossX - 1)
        val minTileY = floor((playerCenterY - maxDistPx) / (chunkSize * GameConfig.TILE_SIZE)).toInt().coerceIn(0, chunksAcrossY - 1)
        val maxTileY = floor((playerCenterY + maxDistPx) / (chunkSize * GameConfig.TILE_SIZE)).toInt().coerceIn(0, chunksAcrossY - 1)

        var closestEntity: Entity? = null
        var closestDistSq = Float.MAX_VALUE

        for (cy in minTileY..maxTileY) {
            for (cx in minTileX..maxTileX) {
                val bucket = chunkBuckets[getChunkIndex(cx, cy)]
                for (i in 0 until bucket.size) {
                    val entity = bucket[i]
                    val nodeComp = nodeLookup(entity)
                    // Only healthy or interactable nodes can be targeted
                    if (nodeComp.state == NodeState.DEPLETED_HIDDEN) continue
                    if (nodeComp.state == NodeState.DEPLETED && nodeComp.currentHealth <= 0) continue

                    val pos = positionLookup(entity)
                    // Node center is offset by half tile
                    val nodeCenterX = pos.x + 8f
                    val nodeCenterY = pos.y + 8f

                    val toX = nodeCenterX - playerCenterX
                    val toY = nodeCenterY - playerCenterY
                    val distSq = toX * toX + toY * toY

                    if (distSq <= maxDistSq) {
                        val dist = sqrt(distSq)
                        // In front dot product check (if very close, allow immediate interaction)
                        val cosAngle = if (dist > 0.001f) (toX * dirX + toY * dirY) / dist else 1f
                        if (cosAngle >= minCos) {
                            if (distSq < closestDistSq) {
                                closestDistSq = distSq
                                closestEntity = entity
                            }
                        }
                    }
                }
            }
        }

        return closestEntity
    }
}
