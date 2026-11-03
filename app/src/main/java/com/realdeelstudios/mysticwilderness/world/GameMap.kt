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

    var nodeSpatialIndex: NodeSpatialIndex? = null

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
     * Unified solidity query checking all layers, map boundaries, and dynamic blocking nodes.
     * Guaranteed safe: returns true (solid) for any out-of-bounds coordinates.
     */
    fun isTileSolid(tileX: Int, tileY: Int): Boolean {
        if (tileX !in 0 until widthInTiles || tileY !in 0 until heightInTiles) {
            return true // Map perimeter boundary is solid
        }
        val chunk = getChunk(tileX / chunkSize, tileY / chunkSize) ?: return true
        if (chunk.isSolidAt(tileX % chunkSize, tileY % chunkSize)) {
            return true
        }
        return nodeSpatialIndex?.isTileBlocked(tileX, tileY) ?: false
    }

    /**
     * Generates the cozy 96x96 placeholder open world:
     * - Grassy meadow with dirt paths
     * - Forest floor in the south-west
     * - Serene pond in the meadow
     * - Dark rocky cave region in the north-east
     * - Solid perimeter boundary wall (2 tiles thick)
     * - A small cozy settlement clearing with wood floor, cabin wall & roof
     */
    fun generatePlaceholderWorld() {
        val grassId = TileRegistry.getId("grass")
        val dirtId = TileRegistry.getId("dirt_path")
        val caveGroundId = TileRegistry.getId("cave_ground")
        val waterId = TileRegistry.getId("water")
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

        // 4. Serene Pond / Lake in meadow (x in 20..32, y in 58..70)
        for (py in 58..68) {
            for (px in 22..34) {
                val dx = px - 28
                val dy = py - 63
                if (dx * dx * 1.0 + dy * dy * 1.5 < 30) {
                    setTile(TileLayer.GROUND, px, py, waterId)
                }
            }
        }

        // 5. Dark Cave Region (North-East: x in 60..90, y in 60..90)
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

        // 6. Cozy Woodland Cabin in Clearing (Center-South: x in 56..64, y in 30..36)
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

    data class NodeSpawnSpec(val nodeDefId: String, val tileX: Int, val tileY: Int)

    /**
     * Deterministic seeded placement of ~150-250 resource nodes across biomes.
     */
    fun generateNodePlacements(): List<NodeSpawnSpec> {
        val specs = ArrayList<NodeSpawnSpec>(250)
        val occupiedTiles = HashSet<Pair<Int, Int>>()

        fun canPlace(tx: Int, ty: Int): Boolean {
            if (tx < 3 || tx >= widthInTiles - 3 || ty < 3 || ty >= heightInTiles - 3) return false
            // Check tile layers directly to avoid circular dependency
            val chunk = getChunk(tx / chunkSize, ty / chunkSize) ?: return false
            if (chunk.isSolidAt(tx % chunkSize, ty % chunkSize)) return false

            // Avoid pond area
            val dx = tx - 28
            val dy = ty - 63
            if (dx * dx * 1.0 + dy * dy * 1.5 < 38) return false

            // Avoid central dirt paths intersection & spawn point (50, 46)
            if (tx in 46..54 && ty in 42..54) return false

            // Avoid cabin and doorway
            if (tx in 54..66 && ty in 28..38) return false

            // Avoid cave wall perimeter
            if (tx in 60..90 && (ty in 60..64 || ty in 86..90)) return false
            if (ty in 60..90 && (tx in 60..64 || tx in 86..90)) return false

            return !occupiedTiles.contains(tx to ty)
        }

        // 1. Forest Region (South-West: x in 6..42, y in 6..42)
        // Trees
        for (cy in 6..42 step 3) {
            for (cx in 6..42 step 3) {
                val jx = ((cx * 17 + cy * 31) % 3) - 1
                val jy = ((cx * 37 + cy * 19) % 3) - 1
                val tx = cx + jx
                val ty = cy + jy
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("tree", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        // Branch piles in forest clearings
        for (cy in 5..43 step 4) {
            for (cx in 7..41 step 4) {
                val tx = cx + ((cy * 13) % 3) - 1
                val ty = cy + ((cx * 23) % 3) - 1
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("branch_pile", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        // 2. Meadow Region (North-West & Center: x in 8..52, y in 46..86)
        // Berry bushes
        for (cy in 48..84 step 5) {
            for (cx in 8..46 step 5) {
                val tx = cx + ((cy * 11) % 3) - 1
                val ty = cy + ((cx * 7) % 3) - 1
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("berry_bush", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        // Grass tufts
        for (cy in 46..86 step 4) {
            for (cx in 6..50 step 4) {
                val tx = cx + ((cy * 29) % 3) - 1
                val ty = cy + ((cx * 17) % 3) - 1
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("grass_tuft", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        // 3. Dark Cave & Rocky Area (North-East: x in 64..86, y in 64..86)
        // Boulders
        for (cy in 65..85 step 4) {
            for (cx in 65..85 step 4) {
                val tx = cx + ((cy * 19) % 3) - 1
                val ty = cy + ((cx * 31) % 3) - 1
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("boulder", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        // Loose stones
        for (cy in 58..86 step 4) {
            for (cx in 56..86 step 4) {
                val tx = cx + ((cy * 7) % 3) - 1
                val ty = cy + ((cx * 13) % 3) - 1
                if (canPlace(tx, ty)) {
                    specs.add(NodeSpawnSpec("loose_stone", tx, ty))
                    occupiedTiles.add(tx to ty)
                }
            }
        }

        return specs
    }
}
