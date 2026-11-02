package com.realdeelstudios.mysticwilderness.ecs.systems

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Family
import com.github.quillraven.fleks.IteratingSystem
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemMetadata
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemTag
import com.realdeelstudios.mysticwilderness.ecs.components.InventoryComponent
import com.realdeelstudios.mysticwilderness.ecs.components.ItemStackComponent
import com.realdeelstudios.mysticwilderness.ecs.components.PickupDelay
import com.realdeelstudios.mysticwilderness.ecs.components.PlayerTag
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import kotlin.math.hypot

class ItemPickupSystem(
    family: Family,
    private val playerFamily: Family,
    private val onInventoryFull: () -> Unit
) : IteratingSystem(family) {

    private val toRemoveList = ArrayList<Entity>()

    override fun onTick() {
        super.onTick()

        // Remove any entities collected this tick
        if (toRemoveList.isNotEmpty()) {
            for (i in 0 until toRemoveList.size) {
                world.minusAssign(toRemoveList[i])
            }
            toRemoveList.clear()
        }

        // Enforce 200 dropped item cap (despawn oldest first)
        val count = family.numEntities
        if (count > 200) {
            val overflow = count - 200
            val sortedList = ArrayList<Pair<Entity, Long>>()
            family.forEach { e ->
                val meta = e[DroppedItemMetadata]
                sortedList.add(e to meta.creationOrder)
            }
            sortedList.sortBy { it.second }
            for (i in 0 until overflow) {
                world.minusAssign(sortedList[i].first)
            }
        }
    }

    override fun onTickEntity(entity: Entity) {
        val delay = entity[PickupDelay]
        val meta = entity[DroppedItemMetadata]
        val dt = GameConfig.FIXED_DELTA_TIME

        meta.bobTimer += dt
        if (delay.remainingSeconds > 0f) {
            delay.remainingSeconds -= dt
            return
        }

        val player = playerFamily.firstOrNull() ?: return
        val playerPos = player[Position]
        val playerCollider = player[Collider]
        val inventory = player[InventoryComponent].inventory
        val itemPos = entity[Position]
        val stackComp = entity[ItemStackComponent]

        val playerCenterX = playerPos.x + playerCollider.offsetX + playerCollider.width / 2f
        val playerCenterY = playerPos.y + playerCollider.offsetY + playerCollider.height / 2f
        val itemCenterX = itemPos.x + 8f
        val itemCenterY = itemPos.y + 8f

        val dx = playerCenterX - itemCenterX
        val dy = playerCenterY - itemCenterY
        val dist = hypot(dx, dy)

        // 1-tile magnet range (~20px)
        if (dist <= 22f) {
            if (dist > 5f) {
                // Magnet attraction toward player
                val speed = 85f * dt
                itemPos.x += (dx / dist) * speed
                itemPos.y += (dy / dist) * speed
            } else {
                // Touching player: attempt pickup
                val currentCount = stackComp.stack.count
                val remainder = inventory.add(stackComp.stack.item, currentCount)
                if (remainder == 0) {
                    toRemoveList.add(entity)
                } else {
                    if (remainder < currentCount) {
                        stackComp.stack.count = remainder
                    }
                    delay.remainingSeconds = 0.5f
                    onInventoryFull()
                }
            }
        }
    }
}
