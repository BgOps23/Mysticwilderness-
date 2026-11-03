package com.realdeelstudios.mysticwilderness.gathering

import com.realdeelstudios.mysticwilderness.data.ItemDef
import com.realdeelstudios.mysticwilderness.data.NodeDef
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import kotlin.random.Random

data class SwingResult(
    val damage: Int,
    val canDamage: Boolean,
    val feedbackMessage: String?
)

data class GatheredDrop(
    val itemId: String,
    val count: Int
)

data class HarvestResult(
    val addedToInventory: List<GatheredDrop>,
    val spilledToWorld: List<GatheredDrop>
)

object GatherLogic {
    const val SWING_INTERVAL_SECONDS = 0.5f

    /**
     * Determines whether a swing can damage a node and calculates the damage dealt.
     */
    fun calculateSwing(nodeDef: NodeDef, toolItem: ItemDef?): SwingResult {
        val toolData = toolItem?.tool
        val requiredTool = nodeDef.requiredToolType

        val matchesToolType = requiredTool != null &&
            toolData != null &&
            toolData.type.equals(requiredTool, ignoreCase = true)

        val hasRequiredTier = matchesToolType && toolData!!.tier >= nodeDef.requiredTier

        return when {
            // Matching tool with sufficient tier: 10 * power
            matchesToolType && hasRequiredTier -> {
                val dmg = (10f * toolData!!.power).toInt().coerceAtLeast(1)
                SwingResult(damage = dmg, canDamage = true, feedbackMessage = null)
            }
            // Bare hands or alternative allowed by node handDamage > 0
            nodeDef.handDamage > 0 -> {
                SwingResult(damage = nodeDef.handDamage, canDamage = true, feedbackMessage = null)
            }
            // Wrong or insufficient tool, hands not allowed
            else -> {
                val firstChar = requiredTool?.firstOrNull()?.lowercaseChar()
                val isVowel = firstChar != null && firstChar in listOf('a', 'e', 'i', 'o', 'u')
                val article = if (isVowel) "an" else "a"
                val msg = if (matchesToolType && !hasRequiredTier) {
                    "Needs a better $requiredTool"
                } else {
                    "Needs $article ${requiredTool ?: "tool"}"
                }
                SwingResult(damage = 0, canDamage = false, feedbackMessage = msg)
            }
        }
    }

    /**
     * Rolls drops defined for the node using the specified random generator.
     */
    fun rollDrops(nodeDef: NodeDef, random: Random = Random.Default): List<GatheredDrop> {
        val result = ArrayList<GatheredDrop>(nodeDef.drops.size)
        nodeDef.drops.forEach { drop ->
            val roll = random.nextFloat()
            if (roll <= drop.chance) {
                val count = if (drop.minCount >= drop.maxCount) {
                    drop.minCount
                } else {
                    random.nextInt(drop.minCount, drop.maxCount + 1)
                }
                if (count > 0) {
                    result.add(GatheredDrop(drop.itemId, count))
                }
            }
        }
        return result
    }

    /**
     * Attempts to place drops into the inventory; any overflow is routed to spilledToWorld.
     * Emits GatherEvent for each gathered drop.
     */
    fun resolveHarvest(
        nodeDef: NodeDef,
        toolType: String?,
        drops: List<GatheredDrop>,
        inventory: InventoryLogic,
        itemLookup: (String) -> ItemDef?
    ): HarvestResult {
        val added = ArrayList<GatheredDrop>()
        val spilled = ArrayList<GatheredDrop>()

        for (i in 0 until drops.size) {
            val drop = drops[i]
            val itemDef = itemLookup(drop.itemId)
            if (itemDef != null) {
                val remainder = inventory.add(itemDef, drop.count)
                val accepted = drop.count - remainder
                if (accepted > 0) {
                    added.add(GatheredDrop(drop.itemId, accepted))
                }
                if (remainder > 0) {
                    spilled.add(GatheredDrop(drop.itemId, remainder))
                }
            } else {
                // If item def missing, treat all as spilled
                spilled.add(drop)
            }

            // Emit gather event
            GatherEventBus.emit(
                GatherEvent(
                    nodeId = nodeDef.id,
                    itemId = drop.itemId,
                    count = drop.count,
                    toolType = toolType
                )
            )
        }

        return HarvestResult(addedToInventory = added, spilledToWorld = spilled)
    }
}
