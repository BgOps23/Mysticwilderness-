package com.realdeelstudios.mysticwilderness.gathering

import com.realdeelstudios.mysticwilderness.data.DepletionBehavior
import com.realdeelstudios.mysticwilderness.data.ItemCategory
import com.realdeelstudios.mysticwilderness.data.ItemDef
import com.realdeelstudios.mysticwilderness.data.NodeDef
import com.realdeelstudios.mysticwilderness.data.NodeDropDef
import com.realdeelstudios.mysticwilderness.data.ToolData
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GatherLogicTest {

    private val treeNode = NodeDef(
        id = "tree",
        name = "Pine Tree",
        spriteRegion = "node_tree",
        depletedSpriteRegion = "node_tree_stump",
        maxHealth = 100,
        requiredToolType = "axe",
        requiredTier = 1,
        handDamage = 0,
        blocksMovement = true,
        drops = listOf(
            NodeDropDef(itemId = "wood", minCount = 3, maxCount = 5, chance = 1.0f)
        ),
        depletionBehavior = DepletionBehavior.stay_depleted_and_regrow
    )

    private val berryBushNode = NodeDef(
        id = "berry_bush",
        name = "Berry Bush",
        spriteRegion = "node_berry_bush",
        depletedSpriteRegion = "node_berry_bush_depleted",
        maxHealth = 10,
        requiredToolType = null,
        requiredTier = 0,
        handDamage = 5,
        blocksMovement = false,
        drops = listOf(
            NodeDropDef(itemId = "berries", minCount = 2, maxCount = 4, chance = 1.0f)
        ),
        depletionBehavior = DepletionBehavior.stay_depleted_and_regrow
    )

    private val stoneAxeTier1 = ItemDef(
        id = "stone_axe",
        name = "Stone Axe",
        category = ItemCategory.tool,
        maxStack = 1,
        iconRegion = "item_stone_axe",
        tool = ToolData(type = "axe", tier = 1, power = 1.5f)
    )

    private val ironAxeTier2 = ItemDef(
        id = "iron_axe",
        name = "Iron Axe",
        category = ItemCategory.tool,
        maxStack = 1,
        iconRegion = "item_stone_axe",
        tool = ToolData(type = "axe", tier = 2, power = 2.5f)
    )

    private val crudeAxeTier0 = ItemDef(
        id = "crude_axe",
        name = "Crude Axe",
        category = ItemCategory.tool,
        maxStack = 1,
        iconRegion = "item_stone_axe",
        tool = ToolData(type = "axe", tier = 0, power = 0.8f)
    )

    private val stonePickaxe = ItemDef(
        id = "stone_pickaxe",
        name = "Stone Pickaxe",
        category = ItemCategory.tool,
        maxStack = 1,
        iconRegion = "item_stone_pickaxe",
        tool = ToolData(type = "pickaxe", tier = 1, power = 1.5f)
    )

    @Test
    fun calculateSwing_matchingToolAndTier_dealsCorrectDamage() {
        val result = GatherLogic.calculateSwing(treeNode, stoneAxeTier1)
        assertTrue(result.canDamage)
        // 10 * 1.5 = 15
        assertEquals(15, result.damage)
        assertNull(result.feedbackMessage)
    }

    @Test
    fun calculateSwing_higherTierTool_dealsCorrectDamage() {
        val result = GatherLogic.calculateSwing(treeNode, ironAxeTier2)
        assertTrue(result.canDamage)
        // 10 * 2.5 = 25
        assertEquals(25, result.damage)
        assertNull(result.feedbackMessage)
    }

    @Test
    fun calculateSwing_lowerTierTool_returnsZeroAndNeedsBetterTool() {
        val result = GatherLogic.calculateSwing(treeNode, crudeAxeTier0)
        assertFalse(result.canDamage)
        assertEquals(0, result.damage)
        assertEquals("Needs a better axe", result.feedbackMessage)
    }

    @Test
    fun calculateSwing_noTool_handsNotAllowed_returnsZeroAndNeedsTool() {
        val result = GatherLogic.calculateSwing(treeNode, null)
        assertFalse(result.canDamage)
        assertEquals(0, result.damage)
        assertEquals("Needs an axe", result.feedbackMessage)
    }

    @Test
    fun calculateSwing_wrongTool_handsAllowed_dealsHandDamage() {
        val result = GatherLogic.calculateSwing(berryBushNode, stonePickaxe)
        assertTrue(result.canDamage)
        assertEquals(5, result.damage)
        assertNull(result.feedbackMessage)
    }

    @Test
    fun calculateSwing_bareHands_handsAllowed_dealsHandDamage() {
        val result = GatherLogic.calculateSwing(berryBushNode, null)
        assertTrue(result.canDamage)
        assertEquals(5, result.damage)
        assertNull(result.feedbackMessage)
    }

    @Test
    fun calculateSwing_wrongTool_handsNotAllowed_returnsNeedsTool() {
        val result = GatherLogic.calculateSwing(treeNode, stonePickaxe)
        assertFalse(result.canDamage)
        assertEquals(0, result.damage)
        assertEquals("Needs an axe", result.feedbackMessage)
    }

    @Test
    fun rollDrops_staysWithinMinMaxAndRespectsChance() {
        val testNode = NodeDef(
            id = "test_node",
            name = "Test",
            spriteRegion = "test",
            maxHealth = 10,
            drops = listOf(
                NodeDropDef(itemId = "wood", minCount = 3, maxCount = 5, chance = 1.0f),
                NodeDropDef(itemId = "rare", minCount = 1, maxCount = 1, chance = 0.0f) // 0% chance
            )
        )

        val rng = Random(42)
        for (i in 0 until 50) {
            val drops = GatherLogic.rollDrops(testNode, rng)
            assertEquals(1, drops.size)
            assertEquals("wood", drops[0].itemId)
            assertTrue("Wood count ${drops[0].count} between 3 and 5", drops[0].count in 3..5)
        }
    }

    @Test
    fun resolveHarvest_fullInventorySendsRemainderToWorld() {
        val inventory = InventoryLogic(size = 1, hotbarSize = 1)
        val woodItem = ItemDef(
            id = "wood",
            name = "Wood",
            category = ItemCategory.resource,
            maxStack = 2,
            iconRegion = "item_wood"
        )
        // Fill the 1 slot with 1 wood
        inventory.add(woodItem, 1)

        val drops = listOf(GatheredDrop(itemId = "wood", count = 3))
        val harvest = GatherLogic.resolveHarvest(
            nodeDef = treeNode,
            toolType = "axe",
            drops = drops,
            inventory = inventory,
            itemLookup = { if (it == "wood") woodItem else null }
        )

        // Only 1 fit into maxStack of 2, remaining 2 spilled to world
        assertEquals(1, harvest.addedToInventory.size)
        assertEquals(1, harvest.addedToInventory[0].count)
        assertEquals(1, harvest.spilledToWorld.size)
        assertEquals(2, harvest.spilledToWorld[0].count)
    }
}
