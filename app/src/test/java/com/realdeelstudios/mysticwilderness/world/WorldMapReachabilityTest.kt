package com.realdeelstudios.mysticwilderness.world

import com.github.quillraven.fleks.Entity
import com.realdeelstudios.mysticwilderness.data.TileRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque

class WorldMapReachabilityTest {

    @Before
    fun setup() {
        val sampleTiles = """
        [
          { "id": "empty", "name": "Empty", "textureRegion": "empty", "solid": false, "walkable": true },
          { "id": "grass", "name": "Grass", "textureRegion": "tile_grass", "solid": false, "walkable": true },
          { "id": "dirt_path", "name": "Dirt", "textureRegion": "tile_dirt", "solid": false, "walkable": true },
          { "id": "cave_ground", "name": "Cave Ground", "textureRegion": "tile_cave_ground", "solid": false, "walkable": true },
          { "id": "water", "name": "Water", "textureRegion": "tile_water", "solid": true, "walkable": false },
          { "id": "cave_wall", "name": "Cave Wall", "textureRegion": "tile_cave_wall", "solid": true, "walkable": false },
          { "id": "border_wall", "name": "Border Wall", "textureRegion": "tile_border_wall", "solid": true, "walkable": false },
          { "id": "wooden_floor", "name": "Wood Floor", "textureRegion": "tile_wood_floor", "solid": false, "walkable": true },
          { "id": "cabin_wall", "name": "Cabin Wall", "textureRegion": "tile_cabin_wall", "solid": true, "walkable": false },
          { "id": "roof_thatch", "name": "Roof", "textureRegion": "tile_roof", "solid": false, "walkable": true }
        ]
        """.trimIndent()
        TileRegistry.load(sampleTiles)
    }

    @Test
    fun spawnClearing_hasNoBlockingNodes() {
        val map = GameMap(96, 96)
        map.generatePlaceholderWorld()
        val placements = map.generateNodePlacements()

        val spawnTileX = 50
        val spawnTileY = 46

        // Check 5-tile radius clearing around spawn (45..55, 41..51)
        for (spec in placements) {
            val inClearing = spec.tileX in (spawnTileX - 4)..(spawnTileX + 4) &&
                spec.tileY in (spawnTileY - 4)..(spawnTileY + 4)
            assertFalse(
                "No node should be placed in spawn clearing, found ${spec.nodeDefId} at (${spec.tileX}, ${spec.tileY})",
                inClearing
            )
        }
    }

    @Test
    fun floodFillFromSpawn_reachesForestMeadowAndRockyRegions() {
        val map = GameMap(96, 96)
        map.generatePlaceholderWorld()

        val spatialIndex = NodeSpatialIndex(96, 96)
        map.nodeSpatialIndex = spatialIndex

        val placements = map.generateNodePlacements()
        // Register blocking nodes (e.g. tree, boulder)
        placements.forEachIndexed { idx, spec ->
            val isBlocking = spec.nodeDefId == "tree" || spec.nodeDefId == "boulder"
            spatialIndex.registerNode(Entity(idx, 0u), spec.tileX, spec.tileY, isBlocking)
        }

        val spawnX = 50
        val spawnY = 46
        assertFalse("Spawn tile itself must be non-solid", map.isTileSolid(spawnX, spawnY))

        // BFS flood-fill from spawn
        val visited = BooleanArray(96 * 96)
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(spawnX to spawnY)
        visited[spawnY * 96 + spawnX] = true

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for (i in 0..3) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]
                if (nx in 0 until 96 && ny in 0 until 96) {
                    val idx = ny * 96 + nx
                    if (!visited[idx] && !map.isTileSolid(nx, ny)) {
                        visited[idx] = true
                        queue.add(nx to ny)
                    }
                }
            }
        }

        // Target destinations across biomes:
        // 1. Forest region (x in 15..25, y in 15..25)
        var forestWalkableCount = 0
        for (fy in 15..25) {
            for (fx in 15..25) {
                if (visited[fy * 96 + fx]) forestWalkableCount++
            }
        }
        assertTrue("Forest region must be reachable from spawn (found $forestWalkableCount accessible tiles)", forestWalkableCount > 20)

        // 2. Meadow region (x in 15..25, y in 45..55)
        var meadowWalkableCount = 0
        for (my in 45..55) {
            for (mx in 15..25) {
                if (visited[my * 96 + mx]) meadowWalkableCount++
            }
        }
        assertTrue("Meadow region must be reachable from spawn (found $meadowWalkableCount accessible tiles)", meadowWalkableCount > 20)

        // 3. Rocky cave cavern interior (x in 70..80, y in 70..80)
        var caveWalkableCount = 0
        for (cy in 70..80) {
            for (cx in 70..80) {
                if (visited[cy * 96 + cx]) caveWalkableCount++
            }
        }
        assertTrue("Cavern interior must be reachable from spawn through cave entrance (found $caveWalkableCount accessible tiles)", caveWalkableCount > 20)
    }
}
