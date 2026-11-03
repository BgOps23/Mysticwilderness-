package com.realdeelstudios.mysticwilderness.ecs.systems

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Family
import com.github.quillraven.fleks.IteratingSystem
import com.realdeelstudios.mysticwilderness.core.GameClock
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import com.realdeelstudios.mysticwilderness.ecs.components.SpriteComponent
import com.realdeelstudios.mysticwilderness.world.NodeSpatialIndex
import kotlin.math.floor

class RespawnSystem(
    family: Family,
    private val playerFamily: Family,
    private val gameClock: GameClock,
    private val spatialIndex: NodeSpatialIndex,
    private val onNodeRespawned: (() -> Unit)? = null
) : IteratingSystem(family) {

    private var secondAccumulator: Float = 0f

    override fun onTick() {
        val dt = GameConfig.FIXED_DELTA_TIME
        secondAccumulator += dt

        // Check pending respawns once per second (not per frame)
        if (secondAccumulator >= 0.99f) {
            secondAccumulator = 0f
            super.onTick()
        }
    }

    override fun onTickEntity(entity: Entity) {
        val nodeComp = entity[ResourceNodeComponent]
        if (nodeComp.state == NodeState.HEALTHY) return

        // Check if respawn time has elapsed
        if (gameClock.totalGameSeconds >= nodeComp.respawnAtGameSeconds) {
            val nodeDef = NodeRegistry.get(nodeComp.nodeDefId) ?: return
            val pos = entity[Position]
            val tx = floor(pos.x / GameConfig.TILE_SIZE).toInt()
            val ty = floor(pos.y / GameConfig.TILE_SIZE).toInt()

            // If node blocks movement, ensure it does not spawn on top of the player
            if (nodeDef.blocksMovement) {
                val player = playerFamily.firstOrNull()
                if (player != null) {
                    val pPos = player[Position]
                    val pCol = player[Collider]

                    val pMinX = pPos.x + pCol.offsetX
                    val pMaxX = pMinX + pCol.width
                    val pMinY = pPos.y + pCol.offsetY
                    val pMaxY = pMinY + pCol.height

                    val nodeMinX = tx * GameConfig.TILE_SIZE
                    val nodeMaxX = nodeMinX + GameConfig.TILE_SIZE
                    val nodeMinY = ty * GameConfig.TILE_SIZE
                    val nodeMaxY = nodeMinY + GameConfig.TILE_SIZE

                    val overlaps = pMinX < nodeMaxX && pMaxX > nodeMinX && pMinY < nodeMaxY && pMaxY > nodeMinY
                    if (overlaps) {
                        // Postpone respawn by 10 seconds to avoid trapping player
                        nodeComp.respawnAtGameSeconds += 10.0
                        return
                    }
                }
            }

            // Restore healthy state
            nodeComp.currentHealth = nodeComp.maxHealth
            nodeComp.state = NodeState.HEALTHY
            nodeComp.respawnAtGameSeconds = 0.0

            entity[SpriteComponent].regionPrefix = nodeDef.spriteRegion

            if (nodeDef.blocksMovement) {
                spatialIndex.setNodeBlocking(entity, tx, ty, isBlocking = true)
            }

            onNodeRespawned?.invoke()
        }
    }
}
