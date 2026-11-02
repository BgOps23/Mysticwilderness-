package com.realdeelstudios.mysticwilderness.world

import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.TileRegistry
import kotlin.math.sin

class GameMap(
    val widthInTiles: Int = 96,
    val heightInTiles: Int = 96,
    val chunkSize: Int = GameConfig.CHUNK_SIZE
) {
    val chunksAcrossX: Int = (widthInTiles + chunkSize - 1) / chunkSize
    val chunksAcrossY: Int = (heightInTiles + chunkSize - 1) / chunkSize
    val totalChunksCount: Int = chunksAcrossX * chunksAcrossY

    val chunks: Array<Chunk> = Array(totalChunksCount) { index ->
        val cx = index % chunksAcrossX
        val cy = index / chunksAcrossX
        Chunk(cx, cy, chunkSize)
    }

    val widthInPx: Float = widthInTiles * GameConfig.TILE_SIZE
    val heightInPx: Float = heightInTiles * GameConfig.TILE_SIZE

    fun getChunk(chunkX: Int, chunkY: Int): Chunk? {
        if (chunkX !in 0 until chunksAcrossX || chunkY !in 0 until chunksAcrossY) return null
        return chunks[chunkY * chunksAcrossX + chunkX]
    }

    fun getChunkForTile(tileX: Int, tileY: Int): Chunk? {
        if (tileX !in 0 until widthInTiles || tileY !in 0 until heightInTiles) return null
        val cx = tileX / chunkSize
        val cy = tileY / chunkSize
        return getChunk(cx, cy)
    }

    fun getTile(layer: TileLayer, tileX: Int, tileY: Int): Short {
        val chunk = getChunkForTile(tileX, tileY) ?: return TileRegistry.EMPTY_TILE_ID
        return chunk.getTile(layer, tileX % chunkSize, tileY % chunkSize)
    }

    fun setTile(layer: TileLayer, tileX: Int, tileY: Int, tileId: Short) {
        val chunk = getChunkForTile(tileX, tileY) ?: return
        chunk.setTile(layer, tileX % chunkSize, tileY % chunkSize, tileId)
    }

    /**
     * Unified solidity query checking all layers and map boundaries.
     * Guaranteed safe: returns true (solid) for any out-of-bounds coordinates.
     */
    fun isTileSolid(tileX: Int, tileY: Int): Boolean {
        if (tileX !in 0 until widthInTiles || tileY !in 0 until heightInTiles) {
            return true // Map perimeter boundary is solid
        }
        val chunk = getChunk(tileX / chunkSize, tileY / chunkSize) ?: return true
        return chunk.isSolidAt(tileX % chunkSize, tileY % chunkSize)
    }

    /**
     * Generates the cozy 96x96 placeholder open world:
     * - Grassy meadow with dirt paths
     * - Forest tree-line with solid trunks and sorting canopies
     * - Dark rocky cave region in the north-east with boulders and cave walls
     * - Solid perimeter boundary wall (2 tiles thick)
     * - A small cozy settlement clearing with wood floor & cabin wall
     */
    fun generatePlaceholderWorld() {
        val grassId = TileRegistry.getId("grass")
        val dirtId = TileRegistry.getId("dirt_path")
        val stoneFloorId = TileRegistry.getId("stone_floor")
        val caveGroundId = TileRegistry.getId("cave_ground")
        val waterId = TileRegistry.getId("water")
        val treeTrunkId = TileRegistry.getId("tree_trunk")
        val treeTopId = TileRegistry.getId("tree_top")
        val rockId = TileRegistry.getId("rock_boulder")
        val caveWallId = TileRegistry.getId("cave_wall")
        val borderWallId = TileRegistry.getId("border_wall")
        val woodFloorId = TileRegistry.getId("wooden_floor")
        val cabinWallId = TileRegistry.getId("cabin_wall")
        val roofId = TileRegistry.getId("roof_thatch")

        // 1. Fill ground with lush grass
        for (y in 0 until heightInTiles) {
            for (x in 0 until widthInTiles) {
                setTile(TileLayer.GROUND, x, y, grassId)
            }
        }

        // 2. Solid border perimeter (2 tiles thick around map)
        for (y in 0 until heightInTiles) {
            for (x in 0 until widthInTiles) {
                if (x < 2 || x >= widthInTiles - 2 || y < 2 || y >= heightInTiles - 2) {
                    setTile(TileLayer.WALL, x, y, borderWallId)
                }
            }
        }

        // 3. Winding dirt paths
        for (x in 10 until 86) {
            val pathY = 48 + (sin(x * 0.1) * 8).toInt()
            if (pathY in 4 until heightInTiles - 4) {
                setTile(TileLayer.FLOOR, x, pathY, dirtId)
                setTile(TileLayer.FLOOR, x, pathY + 1, dirtId)
            }
        }
        for (y in 20 until 80) {
            val pathX = 48 + (sin(y * 0.08) * 6).toInt()
            if (pathX in 4 until widthInTiles - 4) {
                setTile(TileLayer.FLOOR, pathX, y, dirtId)
                setTile(TileLayer.FLOOR, pathX + 1, y, dirtId)
            }
        }

        // 4. Forest Region (South-West: x in 6..42, y in 6..42)
        for (cy in 6..40 step 3) {
            for (cx in 6..40 step 3) {
                // Avoid placing directly on central dirt paths
                if (cx in 44..52 || cy in 44..52) continue
                val jitter = ((cx * 17 + cy * 31) % 3) - 1
                val tx = cx + jitter
                val ty = cy + jitter
                if (tx in 3 until widthInTiles - 3 && ty in 3 until heightInTiles - 4) {
                    // Tree Trunk on OBJECT layer (solid)
                    setTile(TileLayer.OBJECT, tx, ty, treeTrunkId)
                    // Tree Canopy on OBJECT layer directly above trunk (non-solid, sorts over player)
                    setTile(TileLayer.OBJECT, tx, ty + 1, treeTopId)
                }
            }
        }

        // 5. Serene Pond / Lake in meadow (x in 20..32, y in 58..70)
        for (py in 58..68) {
            for (px in 22..34) {
                val dx = px - 28
                val dy = py - 63
                if (dx * dx * 1.0 + dy * dy * 1.5 < 30) {
                    setTile(TileLayer.GROUND, px, py, waterId)
                }
            }
        }

        // 6. Dark Cave Region (North-East: x in 60..90, y in 60..90)
        for (cy in 62..88) {
            for (cx in 62..88) {
                setTile(TileLayer.GROUND, cx, cy, caveGroundId)
            }
        }
        // Cave rock walls enclosing the cavern with an entrance at bottom-left of cave
        for (cy in 62..88) {
            for (cx in 62..88) {
                if (cx == 62 || cx == 88 || cy == 88) {
                    setTile(TileLayer.WALL, cx, cy, caveWallId)
                }
                if (cy == 62 && (cx < 70 || cx > 76)) {
                    // Wall leaving open entrance at x in 70..76
                    setTile(TileLayer.WALL, cx, cy, caveWallId)
                }
            }
        }
        // Scattered rock boulders in cave and near entrance
        val rockCoords = listOf(
            66 to 70, 78 to 80, 82 to 74, 84 to 84, 72 to 85, 68 to 78,
            58 to 58, 59 to 59, 61 to 60
        )
        for ((rx, ry) in rockCoords) {
            setTile(TileLayer.OBJECT, rx, ry, rockId)
        }

        // 7. Cozy Woodland Cabin in Clearing (Center-South: x in 56..64, y in 30..36)
        for (cy in 30..36) {
            for (cx in 56..64) {
                setTile(TileLayer.FLOOR, cx, cy, woodFloorId)
            }
        }
        for (cx in 56..64) {
            setTile(TileLayer.WALL, cx, 36, cabinWallId) // North wall
            if (cx != 60) { // Doorway at x=60
                setTile(TileLayer.WALL, cx, 30, cabinWallId) // South wall
            }
        }
        for (cy in 31..35) {
            setTile(TileLayer.WALL, 56, cy, cabinWallId) // West wall
            setTile(TileLayer.WALL, 64, cy, cabinWallId) // East wall
        }
        // Thatch roof over cabin top
        for (rx in 56..64) {
            setTile(TileLayer.ROOF, rx, 37, roofId)
        }
    }
}
