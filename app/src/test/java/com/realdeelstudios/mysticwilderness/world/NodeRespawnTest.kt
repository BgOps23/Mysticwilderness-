package com.realdeelstudios.mysticwilderness.world

import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import com.realdeelstudios.mysticwilderness.core.GameClock
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.DepletionBehavior
import com.realdeelstudios.mysticwilderness.data.NodeDef
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.FacingComponent
import com.realdeelstudios.mysticwilderness.ecs.components.InventoryComponent
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.PlayerTag
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import com.realdeelstudios.mysticwilderness.ecs.components.SpriteComponent
import com.realdeelstudios.mysticwilderness.ecs.systems.RespawnSystem
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeRespawnTest {

    private val blockingTree = NodeDef(
        id = "tree",
        name = "Tree",
        spriteRegion = "node_tree",
        depletedSpriteRegion = "node_tree_stump",
        maxHealth = 100,
        blocksMovement = true,
        depletionBehavior = DepletionBehavior.stay_depleted_and_regrow
    )

    @Before
    fun setup() {
        NodeRegistry.clear()
        val json = """
        [
          {
            "id": "tree",
            "name": "Tree",
            "spriteRegion": "node_tree",
            "depletedSpriteRegion": "node_tree_stump",
            "maxHealth": 100,
            "blocksMovement": true,
            "depletionBehavior": "stay_depleted_and_regrow"
          }
        ]
        """.trimIndent()
        NodeRegistry.load(json, itemValidator = { true }, atlasValidator = { true })
    }

    @Test
    fun respawnSystem_restoresNodeWhenTimeExpires() {
        val clock = GameClock(0.0)
        val spatialIndex = NodeSpatialIndex(96, 96)

        lateinit var respawnSystem: RespawnSystem
        val world = configureWorld {
            val nodeFam = World.family { all(ResourceNodeComponent, Position, SpriteComponent) }
            val pFam = World.family { all(PlayerTag, Position, Collider) }

            systems {
                val rs = RespawnSystem(nodeFam, pFam, clock, spatialIndex)
                respawnSystem = rs
                add(rs)
            }
        }

        // Spawn depleted tree at (100, 100) scheduled for t=50.0s
        val treeEntity = world.entity {
            it += Position(100f, 100f, 100f, 100f)
            it += SpriteComponent(regionPrefix = "node_tree_stump")
            it += ResourceNodeComponent(
                nodeDefId = "tree",
                currentHealth = 0,
                maxHealth = 100,
                state = NodeState.DEPLETED,
                respawnAtGameSeconds = 50.0
            )
        }

        // Player far away at (0, 0)
        world.entity {
            it += PlayerTag()
            it += Position(0f, 0f, 0f, 0f)
            it += Collider(10f, 6f, 3f, 1f)
        }

        // Advance to 30s (not yet)
        clock.tick(30f)
        for (i in 0 until 60) world.update(1f / 60f)
        with(world) {
            assertEquals(NodeState.DEPLETED, treeEntity[ResourceNodeComponent].state)
        }

        // Advance to 55s (elapsed)
        clock.tick(25f)
        for (i in 0 until 60) world.update(1f / 60f)

        with(world) {
            val nodeComp = treeEntity[ResourceNodeComponent]
            assertEquals(NodeState.HEALTHY, nodeComp.state)
            assertEquals(100, nodeComp.currentHealth)
            assertEquals("node_tree", treeEntity[SpriteComponent].regionPrefix)
        }
    }

    @Test
    fun respawnSystem_postponesRespawnIfPlayerOverlaps() {
        val clock = GameClock(50.0)
        val spatialIndex = NodeSpatialIndex(96, 96)

        val world = configureWorld {
            val nodeFam = World.family { all(ResourceNodeComponent, Position, SpriteComponent) }
            val pFam = World.family { all(PlayerTag, Position, Collider) }

            systems {
                add(RespawnSystem(nodeFam, pFam, clock, spatialIndex))
            }
        }

        // Tree tile at tile (10, 10) -> x=160, y=160
        val treeEntity = world.entity {
            it += Position(160f, 160f, 160f, 160f)
            it += SpriteComponent(regionPrefix = "node_tree_stump")
            it += ResourceNodeComponent(
                nodeDefId = "tree",
                currentHealth = 0,
                maxHealth = 100,
                state = NodeState.DEPLETED,
                respawnAtGameSeconds = 50.0
            )
        }

        // Player standing directly inside tree tile (162, 162)
        world.entity {
            it += PlayerTag()
            it += Position(162f, 162f, 162f, 162f)
            it += Collider(10f, 6f, 3f, 1f)
        }

        // Run respawn check (needs 1s accumulated in system = 60 ticks)
        for (i in 0 until 60) world.update(1f / 60f)

        with(world) {
            val nodeComp = treeEntity[ResourceNodeComponent]
            // Must still be depleted
            assertEquals(NodeState.DEPLETED, nodeComp.state)
            // Respawn timer postponed by 10 seconds -> 60.0s
            assertEquals(60.0, nodeComp.respawnAtGameSeconds, 0.001)
        }
    }
}
