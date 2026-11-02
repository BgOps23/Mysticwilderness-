package com.realdeelstudios.mysticwilderness.world

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.data.TileRegistry
import kotlin.math.max
import kotlin.math.min

class WorldRenderer(
    private val gameMap: GameMap,
    private val atlasProvider: IAtlasProvider,
    private val batch: SpriteBatch
) {
    var chunksRenderedCount: Int = 0
        private set

    // Visible chunk bounds (inclusive)
    var minChunkX = 0
        private set
    var maxChunkX = 0
        private set
    var minChunkY = 0
        private set
    var maxChunkY = 0
        private set

    // Visible tile bounds (inclusive)
    var minTileX = 0
        private set
    var maxTileX = 0
        private set
    var minTileY = 0
        private set
    var maxTileY = 0
        private set

    fun updateFrustumCulling(camera: OrthographicCamera, viewportWidth: Float, viewportHeight: Float) {
        val halfW = viewportWidth / 2f
        val halfH = viewportHeight / 2f
        val camX = camera.position.x
        val camY = camera.position.y

        val minX = camX - halfW
        val maxX = camX + halfW
        val minY = camY - halfH
        val maxY = camY + halfH

        val chunkPx = gameMap.chunkSize * GameConfig.TILE_SIZE

        // +1 chunk margin as requested
        minChunkX = max(0, (minX / chunkPx).toInt() - 1)
        maxChunkX = min(gameMap.chunksAcrossX - 1, (maxX / chunkPx).toInt() + 1)
        minChunkY = max(0, (minY / chunkPx).toInt() - 1)
        maxChunkY = min(gameMap.chunksAcrossY - 1, (maxY / chunkPx).toInt() + 1)

        chunksRenderedCount = (maxChunkX - minChunkX + 1) * (maxChunkY - minChunkY + 1)

        // Tile bounds corresponding to visible chunks
        minTileX = minChunkX * gameMap.chunkSize
        maxTileX = min(gameMap.widthInTiles - 1, (maxChunkX + 1) * gameMap.chunkSize - 1)
        minTileY = minChunkY * gameMap.chunkSize
        maxTileY = min(gameMap.heightInTiles - 1, (maxChunkY + 1) * gameMap.chunkSize - 1)
    }

    /**
     * Pass 1: Render Ground and Floor layers for all visible chunks.
     */
    fun renderGroundAndFloor() {
        val tileSize = GameConfig.TILE_SIZE

        for (cy in minChunkY..maxChunkY) {
            for (cx in minChunkX..maxChunkX) {
                val chunk = gameMap.getChunk(cx, cy) ?: continue
                val cWorldX = chunk.worldX
                val cWorldY = chunk.worldY

                // Draw GROUND layer
                val groundTiles = chunk.layerTiles[TileLayer.GROUND.ordinal]
                for (ly in 0 until gameMap.chunkSize) {
                    val drawY = cWorldY + ly * tileSize
                    val rowOffset = ly * gameMap.chunkSize
                    for (lx in 0 until gameMap.chunkSize) {
                        val tileId = groundTiles[rowOffset + lx]
                        if (tileId != TileRegistry.EMPTY_TILE_ID) {
                            val def = TileRegistry.getDef(tileId) ?: continue
                            val region = atlasProvider.getRegion(def.textureRegion)
                            batch.draw(region, cWorldX + lx * tileSize, drawY, tileSize, tileSize)
                        }
                    }
                }

                // Draw FLOOR layer
                val floorTiles = chunk.layerTiles[TileLayer.FLOOR.ordinal]
                for (ly in 0 until gameMap.chunkSize) {
                    val drawY = cWorldY + ly * tileSize
                    val rowOffset = ly * gameMap.chunkSize
                    for (lx in 0 until gameMap.chunkSize) {
                        val tileId = floorTiles[rowOffset + lx]
                        if (tileId != TileRegistry.EMPTY_TILE_ID) {
                            val def = TileRegistry.getDef(tileId) ?: continue
                            val region = atlasProvider.getRegion(def.textureRegion)
                            batch.draw(region, cWorldX + lx * tileSize, drawY, tileSize, tileSize)
                        }
                    }
                }
            }
        }
    }

    /**
     * Renders a single row of WALL and OBJECT tiles.
     * Sort anchor is defined as the base row (tileY * 16f).
     */
    fun renderWallsAndObjectsForRow(tileY: Int) {
        val tileSize = GameConfig.TILE_SIZE
        val drawY = tileY * tileSize
        val cy = tileY / gameMap.chunkSize
        val ly = tileY % gameMap.chunkSize
        val rowOffset = ly * gameMap.chunkSize

        for (cx in minChunkX..maxChunkX) {
            val chunk = gameMap.getChunk(cx, cy) ?: continue
            val cWorldX = chunk.worldX

            // 1. WALL layer on this row
            val wallTiles = chunk.layerTiles[TileLayer.WALL.ordinal]
            for (lx in 0 until gameMap.chunkSize) {
                val tileId = wallTiles[rowOffset + lx]
                if (tileId != TileRegistry.EMPTY_TILE_ID) {
                    val def = TileRegistry.getDef(tileId) ?: continue
                    val region = atlasProvider.getRegion(def.textureRegion)
                    batch.draw(region, cWorldX + lx * tileSize, drawY, tileSize, tileSize)
                }
            }

            // 2. OBJECT layer on this row (tree trunks, canopies, boulders)
            val objectTiles = chunk.layerTiles[TileLayer.OBJECT.ordinal]
            for (lx in 0 until gameMap.chunkSize) {
                val tileId = objectTiles[rowOffset + lx]
                if (tileId != TileRegistry.EMPTY_TILE_ID) {
                    val def = TileRegistry.getDef(tileId) ?: continue
                    val region = atlasProvider.getRegion(def.textureRegion)
                    batch.draw(region, cWorldX + lx * tileSize, drawY, tileSize, tileSize)
                }
            }
        }
    }

    /**
     * Pass 3: Render Roof layer on top of all entities and objects.
     */
    fun renderRoofs() {
        val tileSize = GameConfig.TILE_SIZE

        for (cy in minChunkY..maxChunkY) {
            for (cx in minChunkX..maxChunkX) {
                val chunk = gameMap.getChunk(cx, cy) ?: continue
                val cWorldX = chunk.worldX
                val cWorldY = chunk.worldY
                val roofTiles = chunk.layerTiles[TileLayer.ROOF.ordinal]

                for (ly in 0 until gameMap.chunkSize) {
                    val drawY = cWorldY + ly * tileSize
                    val rowOffset = ly * gameMap.chunkSize
                    for (lx in 0 until gameMap.chunkSize) {
                        val tileId = roofTiles[rowOffset + lx]
                        if (tileId != TileRegistry.EMPTY_TILE_ID) {
                            val def = TileRegistry.getDef(tileId) ?: continue
                            val region = atlasProvider.getRegion(def.textureRegion)
                            batch.draw(region, cWorldX + lx * tileSize, drawY, tileSize, tileSize)
                        }
                    }
                }
            }
        }
    }
}
