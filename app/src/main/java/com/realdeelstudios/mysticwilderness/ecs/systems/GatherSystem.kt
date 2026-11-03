package com.realdeelstudios.mysticwilderness.ecs.systems

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Family
import com.github.quillraven.fleks.IteratingSystem
import com.realdeelstudios.mysticwilderness.core.GameClock
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.DepletionBehavior
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.Direction
import com.realdeelstudios.mysticwilderness.ecs.components.FacingComponent
import com.realdeelstudios.mysticwilderness.ecs.components.InventoryComponent
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import com.realdeelstudios.mysticwilderness.ecs.components.SpriteComponent
import com.realdeelstudios.mysticwilderness.gathering.GatherLogic
import com.realdeelstudios.mysticwilderness.input.IInputState
import com.realdeelstudios.mysticwilderness.inventory.ItemStack
import com.realdeelstudios.mysticwilderness.world.NodeSpatialIndex
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

class GatherSystem(
    family: Family,
    private val playerFamily: Family,
    private val inputState: IInputState,
    private val gameClock: GameClock,
    private val spatialIndex: NodeSpatialIndex,
    private val onTriggerHudMessage: (String) -> Unit,
    private val onSpawnWorldItem: (ItemStack, Float, Float) -> Unit,
    private val random: Random = Random.Default
) : IteratingSystem(family) {

    var currentTargetEntity: Entity? = null
        private set

    private var swingCooldown: Float = 0f
    private var warningCooldown: Float = 0f
    private var targetCacheDirty: Boolean = true

    private var lastPlayerX: Float = Float.NaN
    private var lastPlayerY: Float = Float.NaN
    private var lastPlayerFacing: Direction = Direction.DOWN

    fun invalidateTargetCache() {
        targetCacheDirty = true
    }

    override fun onTick() {
        val dt = GameConfig.FIXED_DELTA_TIME
        if (swingCooldown > 0f) swingCooldown -= dt
        if (warningCooldown > 0f) warningCooldown -= dt

        // 1. Update feedback timers on all resource nodes
        super.onTick()

        // 2. Process player interaction
        var pEntity: Entity? = null
        playerFamily.forEach {
            pEntity = it
            return@forEach
        }
        val player = pEntity ?: return

        val pPos = player[Position]
        val pFacing = player[FacingComponent]
        val pInv = player[InventoryComponent]

        val playerCenterX = pPos.x + 8f
        val playerCenterY = pPos.y + 4f

        // Check if player moved or turned
        val moved = abs(pPos.x - lastPlayerX) > 0.1f || abs(pPos.y - lastPlayerY) > 0.1f
        val turned = pFacing.direction != lastPlayerFacing

        if (moved || turned || targetCacheDirty) {
            currentTargetEntity = spatialIndex.findTarget(
                playerCenterX = playerCenterX,
                playerCenterY = playerCenterY,
                facing = pFacing.direction,
                positionLookup = { it[Position] },
                nodeLookup = { it[ResourceNodeComponent] }
            )
            lastPlayerX = pPos.x
            lastPlayerY = pPos.y
            lastPlayerFacing = pFacing.direction
            targetCacheDirty = false
        }

        // Action button interaction
        if (inputState.isActionPressed && swingCooldown <= 0f) {
            val target = currentTargetEntity
            if (target != null && target in family) {
                performGather(target, pInv)
            }
            swingCooldown = GatherLogic.SWING_INTERVAL_SECONDS
        }
    }

    override fun onTickEntity(entity: Entity) {
        val nodeComp = entity[ResourceNodeComponent]
        val dt = GameConfig.FIXED_DELTA_TIME
        if (nodeComp.hitShakeTimer > 0f) nodeComp.hitShakeTimer = max(0f, nodeComp.hitShakeTimer - dt)
        if (nodeComp.damageFlashTimer > 0f) nodeComp.damageFlashTimer = max(0f, nodeComp.damageFlashTimer - dt)
        if (nodeComp.showHealthBarTimer > 0f) nodeComp.showHealthBarTimer = max(0f, nodeComp.showHealthBarTimer - dt)
    }

    private fun performGather(target: Entity, playerInventory: InventoryComponent) {
        val nodeComp = target[ResourceNodeComponent]
        val nodeDef = NodeRegistry.get(nodeComp.nodeDefId) ?: return

        // Cannot harvest depleted nodes
        if (nodeComp.state == NodeState.DEPLETED_HIDDEN ||
            (nodeComp.state == NodeState.DEPLETED && nodeComp.currentHealth <= 0)
        ) {
            invalidateTargetCache()
            return
        }

        val activeToolItem = playerInventory.inventory.selectedHotbarItem?.item
        val swingResult = GatherLogic.calculateSwing(nodeDef, activeToolItem)

        if (swingResult.canDamage) {
            nodeComp.currentHealth = max(0, nodeComp.currentHealth - swingResult.damage)
            nodeComp.hitShakeTimer = 0.15f
            nodeComp.damageFlashTimer = 0.15f
            nodeComp.showHealthBarTimer = 2.0f

            if (nodeComp.currentHealth == 0) {
                // Depleted: Roll drops & harvest
                val rolledDrops = GatherLogic.rollDrops(nodeDef, random)
                val harvest = GatherLogic.resolveHarvest(
                    nodeDef = nodeDef,
                    toolType = activeToolItem?.tool?.type,
                    drops = rolledDrops,
                    inventory = playerInventory.inventory,
                    itemLookup = { ItemRegistry.get(it) }
                )

                // Spawn any spillover into the world
                val targetPos = target[Position]
                harvest.spilledToWorld.forEach { spill ->
                    val itemDef = ItemRegistry.get(spill.itemId)
                    if (itemDef != null) {
                        onSpawnWorldItem(
                            ItemStack(itemDef, spill.count),
                            targetPos.x + 2f,
                            targetPos.y + 2f
                        )
                    }
                }

                if (harvest.spilledToWorld.isNotEmpty()) {
                    onTriggerHudMessage("Inventory full!")
                }

                // Apply depletion behavior
                val tx = floor(targetPos.x / GameConfig.TILE_SIZE).toInt()
                val ty = floor(targetPos.y / GameConfig.TILE_SIZE).toInt()

                val minRespawn = nodeDef.respawnGameSeconds.min
                val maxRespawn = nodeDef.respawnGameSeconds.max
                val respawnDelay = if (minRespawn >= maxRespawn) minRespawn else random.nextFloat() * (maxRespawn - minRespawn) + minRespawn
                nodeComp.respawnAtGameSeconds = gameClock.totalGameSeconds + respawnDelay.toDouble()

                when (nodeDef.depletionBehavior) {
                    DepletionBehavior.stay_depleted_and_regrow -> {
                        nodeComp.state = NodeState.DEPLETED
                        target[SpriteComponent].regionPrefix = nodeDef.depletedSpriteRegion ?: nodeDef.spriteRegion
                        spatialIndex.setNodeBlocking(target, tx, ty, isBlocking = false)
                    }
                    DepletionBehavior.remove_and_respawn -> {
                        nodeComp.state = NodeState.DEPLETED_HIDDEN
                        spatialIndex.setNodeBlocking(target, tx, ty, isBlocking = false)
                    }
                }

                // State changed: invalidate cached target
                invalidateTargetCache()
            }
        } else {
            // Cannot damage: show throttled message (at most 1 per second)
            val msg = swingResult.feedbackMessage
            if (msg != null && warningCooldown <= 0f) {
                onTriggerHudMessage(msg)
                warningCooldown = 1.0f
            }
        }
    }
}
