package com.realdeelstudios.mysticwilderness.world

import com.github.quillraven.fleks.Entity
import com.realdeelstudios.mysticwilderness.core.GameClock
import com.realdeelstudios.mysticwilderness.core.screens.GameScreen.WorldSaveData
import com.realdeelstudios.mysticwilderness.data.DepletionBehavior
import com.realdeelstudios.mysticwilderness.data.NodeDef
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.NodeSaveData
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeSaveDataRoundTripTest {

    @Before
    fun setup() {
        NodeRegistry.clear()
        val json = """
        [
          {
            "id": "tree",
            "name": "Pine Tree",
            "spriteRegion": "node_tree",
            "depletedSpriteRegion": "node_tree_stump",
            "maxHealth": 100,
            "blocksMovement": true,
            "depletionBehavior": "stay_depleted_and_regrow"
          },
          {
            "id": "berry_bush",
            "name": "Berry Bush",
            "spriteRegion": "node_berry_bush",
            "depletedSpriteRegion": "node_berry_bush_depleted",
            "maxHealth": 10,
            "blocksMovement": false,
            "depletionBehavior": "stay_depleted_and_regrow"
          }
        ]
        """.trimIndent()
        NodeRegistry.load(json, itemValidator = { true }, atlasValidator = { true })
    }

    @Test
    fun roundTrip_savesOnlyModifiedNodesAndRestoresState() {
        val clock = GameClock(250.0)

        // Mock 3 nodes:
        // 1. tree1: full health (unmodified default)
        val tree1Pos = Position(100f, 100f)
        val tree1Comp = ResourceNodeComponent("tree", 100, 100, NodeState.HEALTHY, 0.0)

        // 2. tree2: damaged to 60/100 (modified)
        val tree2Pos = Position(120f, 100f)
        val tree2Comp = ResourceNodeComponent("tree", 60, 100, NodeState.HEALTHY, 0.0)

        // 3. bush: depleted, respawn at t=1000s (modified)
        val bushPos = Position(200f, 200f)
        val bushComp = ResourceNodeComponent("berry_bush", 0, 10, NodeState.DEPLETED, 1000.0)

        val allNodes = listOf(
            (tree1Pos to tree1Comp),
            (tree2Pos to tree2Comp),
            (bushPos to bushComp)
        )

        // Serialize: only modified nodes should be included
        val modifiedList = ArrayList<NodeSaveData>()
        allNodes.forEach { (pos, comp) ->
            val def = NodeRegistry.get(comp.nodeDefId)!!
            val isDefault = comp.currentHealth == def.maxHealth &&
                comp.state == NodeState.HEALTHY &&
                comp.respawnAtGameSeconds <= 0.0

            if (!isDefault) {
                modifiedList.add(
                    NodeSaveData(
                        entityX = pos.x,
                        entityY = pos.y,
                        nodeDefId = comp.nodeDefId,
                        health = comp.currentHealth,
                        state = comp.state,
                        respawnAtGameSeconds = comp.respawnAtGameSeconds
                    )
                )
            }
        }

        val mapSeed = 1337L
        val saveData = WorldSaveData(
            mapSeed = mapSeed,
            clockData = clock.toSaveData(),
            modifiedNodes = modifiedList
        )

        // Verify that default tree1 was NOT saved, only 2 modified nodes were saved
        assertEquals(2, saveData.modifiedNodes.size)
        assertEquals(1337L, saveData.mapSeed)
        assertEquals(250.0, saveData.clockData.totalGameSeconds, 0.001)

        // Restore into fresh world instances
        val restoredClock = GameClock(0.0)
        restoredClock.fromSaveData(saveData.clockData)
        assertEquals(250.0, restoredClock.totalGameSeconds, 0.001)

        val freshTree1 = ResourceNodeComponent("tree", 100, 100, NodeState.HEALTHY)
        val freshTree2 = ResourceNodeComponent("tree", 100, 100, NodeState.HEALTHY)
        val freshBush = ResourceNodeComponent("berry_bush", 10, 10, NodeState.HEALTHY)

        val freshNodes = mapOf(
            (100 to 100) to freshTree1,
            (120 to 100) to freshTree2,
            (200 to 200) to freshBush
        )

        saveData.modifiedNodes.forEach { saved ->
            val key = saved.entityX.toInt() to saved.entityY.toInt()
            val target = freshNodes[key]!!
            target.currentHealth = saved.health
            target.state = saved.state
            target.respawnAtGameSeconds = saved.respawnAtGameSeconds
        }

        // Verify restoration
        assertEquals(100, freshTree1.currentHealth)
        assertEquals(NodeState.HEALTHY, freshTree1.state)

        assertEquals(60, freshTree2.currentHealth)
        assertEquals(NodeState.HEALTHY, freshTree2.state)

        assertEquals(0, freshBush.currentHealth)
        assertEquals(NodeState.DEPLETED, freshBush.state)
        assertEquals(1000.0, freshBush.respawnAtGameSeconds, 0.001)
    }
}
