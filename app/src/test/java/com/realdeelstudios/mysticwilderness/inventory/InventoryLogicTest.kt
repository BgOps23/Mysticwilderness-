package com.realdeelstudios.mysticwilderness.inventory

import com.realdeelstudios.mysticwilderness.data.ItemCategory
import com.realdeelstudios.mysticwilderness.data.ItemDef
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import com.realdeelstudios.mysticwilderness.data.ToolData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InventoryLogicTest {

    private val wood = ItemDef(
        id = "wood",
        name = "Wood",
        description = "Sturdy timber.",
        category = ItemCategory.resource,
        maxStack = 99,
        iconRegion = "item_wood"
    )

    private val stone = ItemDef(
        id = "stone",
        name = "Stone",
        description = "Rough stone.",
        category = ItemCategory.resource,
        maxStack = 99,
        iconRegion = "item_stone"
    )

    private val berries = ItemDef(
        id = "berries",
        name = "Berries",
        description = "Sweet wild berries.",
        category = ItemCategory.consumable,
        maxStack = 50,
        iconRegion = "item_berries"
    )

    private val axe = ItemDef(
        id = "stone_axe",
        name = "Stone Axe",
        description = "Basic axe.",
        category = ItemCategory.tool,
        maxStack = 1,
        iconRegion = "item_stone_axe",
        tool = ToolData(type = "axe", tier = 1, power = 1.0f)
    )

    @Before
    fun setup() {
        val json = """
            [
              {"id": "wood", "name": "Wood", "category": "resource", "maxStack": 99, "iconRegion": "item_wood"},
              {"id": "stone", "name": "Stone", "category": "resource", "maxStack": 99, "iconRegion": "item_stone"},
              {"id": "berries", "name": "Berries", "category": "consumable", "maxStack": 50, "iconRegion": "item_berries"},
              {"id": "stone_axe", "name": "Stone Axe", "category": "tool", "maxStack": 1, "iconRegion": "item_stone_axe"}
            ]
        """.trimIndent()
        ItemRegistry.load(json)
    }

    @Test
    fun testStackingUpToMaxStack() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Adding 60 wood: fits into slot 0
        val rem1 = inventory.add(wood, 60)
        assertEquals(0, rem1)
        assertEquals(60, inventory.get(0)?.count)

        // Adding 50 wood: fills slot 0 up to 99 (added 39), remaining 11 overflows to slot 1
        val rem2 = inventory.add(wood, 50)
        assertEquals(0, rem2)
        assertEquals(99, inventory.get(0)?.count)
        assertEquals(11, inventory.get(1)?.count)
        assertEquals(110, inventory.countOf(wood))
    }

    @Test
    fun testRemainderReturnedOnFullInventory() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Fill all 20 slots with stone up to maxStack (99 * 20 = 1980)
        for (i in 0 until 20) {
            inventory.set(i, ItemStack(stone, 99))
        }
        assertTrue(inventory.isFull())

        // Attempting to add 15 more wood when inventory is full
        val remainder = inventory.add(wood, 15)
        assertEquals(15, remainder)
        assertEquals(0, inventory.countOf(wood))
        assertEquals(1980, inventory.countOf(stone))
    }

    @Test
    fun testMoveSwapVsMerge() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Move to empty slot
        inventory.set(0, ItemStack(wood, 20))
        inventory.move(0, 5)
        assertNull(inventory.get(0))
        assertEquals(20, inventory.get(5)?.count)

        // Move to slot with same item: merges up to maxStack
        inventory.set(0, ItemStack(wood, 80))
        inventory.move(5, 0) // from slot 5 (20) to slot 0 (80)
        // Slot 0 maxStack is 99 -> takes 19, slot 5 keeps remainder 1
        assertEquals(99, inventory.get(0)?.count)
        assertEquals(1, inventory.get(5)?.count)

        // Move to slot with different item: swaps slots
        inventory.set(1, ItemStack(axe, 1))
        inventory.move(1, 0)
        assertEquals(axe.id, inventory.get(0)?.item?.id)
        assertEquals(wood.id, inventory.get(1)?.item?.id)
        assertEquals(99, inventory.get(1)?.count)
    }

    @Test
    fun testSplitWithOddCounts() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Slot with count 5: 5 / 2 = 2 moves, 3 remains in slot
        inventory.set(0, ItemStack(wood, 5))
        val splitSuccess = inventory.split(0)
        assertTrue(splitSuccess)
        assertEquals(3, inventory.get(0)?.count)
        assertEquals(2, inventory.get(1)?.count)

        // Stack with count 1 cannot be split
        inventory.set(2, ItemStack(axe, 1))
        val splitFail = inventory.split(2)
        assertFalse(splitFail)
        assertEquals(1, inventory.get(2)?.count)
    }

    @Test
    fun testRemoveAcrossMultipleStacks() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        inventory.set(0, ItemStack(wood, 10))
        inventory.set(1, ItemStack(wood, 5))
        inventory.set(2, ItemStack(stone, 20))

        assertEquals(15, inventory.countOf(wood))

        // Remove 12 wood: consumes slot 0 entirely (10), takes 2 from slot 1 (3 left)
        val removed = inventory.remove(wood, 12)
        assertTrue(removed)
        assertNull(inventory.get(0))
        assertEquals(3, inventory.get(1)?.count)
        assertEquals(3, inventory.countOf(wood))
        assertEquals(20, inventory.countOf(stone))

        // Try removing more than exists: fails and modifies nothing
        val removeFail = inventory.remove(wood, 10)
        assertFalse(removeFail)
        assertEquals(3, inventory.countOf(wood))
    }

    @Test
    fun testSortAffectsOnlyBackpackSlots5To19() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Configure Hotbar slots (0..4)
        inventory.set(0, ItemStack(stone, 10))
        inventory.set(1, ItemStack(axe, 1))
        inventory.set(2, null)
        inventory.set(3, ItemStack(berries, 5))
        inventory.set(4, null)

        // Configure Backpack slots (5..19) with scattered, partial stacks
        inventory.set(5, ItemStack(wood, 20))
        inventory.set(8, ItemStack(berries, 15))
        inventory.set(10, ItemStack(wood, 10))
        inventory.set(14, ItemStack(stone, 25))
        inventory.set(17, ItemStack(berries, 10))

        // Perform sort
        inventory.sort()

        // 1. Hotbar slots (0..4) MUST remain completely untouched
        assertEquals(stone.id, inventory.get(0)?.item?.id)
        assertEquals(10, inventory.get(0)?.count)
        assertEquals(axe.id, inventory.get(1)?.item?.id)
        assertEquals(1, inventory.get(1)?.count)
        assertNull(inventory.get(2))
        assertEquals(berries.id, inventory.get(3)?.item?.id)
        assertEquals(5, inventory.get(3)?.count)
        assertNull(inventory.get(4))

        // 2. Backpack slots (5..19) are merged and sorted by Category ordinal, then name
        // Category ordinals: tool(0), resource(1), consumable(2), placeable(3), misc(4)
        // Backpack resources: Stone (25), Wood (20+10=30)
        // Resource names: Stone, Wood
        // Backpack consumables: Berries (15+10=25)
        // So expected order in backpack:
        // Slot 5: Stone (25)
        // Slot 6: Wood (30)
        // Slot 7: Berries (25)
        // Slots 8..19: null
        val slot5 = inventory.get(5)
        assertNotNull(slot5)
        assertEquals("stone", slot5?.item?.id)
        assertEquals(25, slot5?.count)

        val slot6 = inventory.get(6)
        assertNotNull(slot6)
        assertEquals("wood", slot6?.item?.id)
        assertEquals(30, slot6?.count)

        val slot7 = inventory.get(7)
        assertNotNull(slot7)
        assertEquals("berries", slot7?.item?.id)
        assertEquals(25, slot7?.count)

        for (i in 8 until 20) {
            assertNull("Slot $i should be empty after sort", inventory.get(i))
        }
    }

    @Test
    fun testPartialPickupReducesStackAndKeepsEntityWithoutLoss() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)

        // Fill 19 slots with stone so only 1 slot has room
        for (i in 0 until 19) {
            inventory.set(i, ItemStack(stone, 99))
        }
        // Slot 19 has 93 wood, so exactly 6 wood can fit
        inventory.set(19, ItemStack(wood, 93))

        // World item contains 20 wood
        val worldItemStack = ItemStack(wood, 20)
        val initialTotalWood = inventory.countOf(wood) + worldItemStack.count // 93 + 20 = 113

        // Simulate pickup logic from ItemPickupSystem
        val remainder = inventory.add(worldItemStack.item, worldItemStack.count)
        assertEquals(14, remainder)

        if (remainder > 0 && remainder < worldItemStack.count) {
            // Partial pickup: reduce world item stack to remainder, do NOT delete entity
            worldItemStack.count = remainder
        }

        // Verify world item now has exactly the 14 remaining items
        assertEquals(14, worldItemStack.count)
        // Verify inventory slot 19 is now full at 99
        assertEquals(99, inventory.get(19)?.count)
        // Verify no items were duplicated or lost
        val finalTotalWood = inventory.countOf(wood) + worldItemStack.count // 99 + 14 = 113
        assertEquals(initialTotalWood, finalTotalWood)
    }

    @Test
    fun testSaveAndLoadRoundTripWithUnknownItem() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)
        inventory.set(0, ItemStack(wood, 50))
        inventory.set(3, ItemStack(berries, 25))

        val saveData = inventory.toSaveData().toMutableList()
        // Inject an unknown item ID into slot 7
        saveData.add(SlotSaveData(slot = 7, itemId = "unknown_relic", count = 5))

        val warnings = ArrayList<String>()
        val restoredInventory = InventoryLogic(size = 20, hotbarSize = 5)
        restoredInventory.fromSaveData(saveData) { warning ->
            warnings.add(warning)
        }

        // Verify valid slots restored
        assertEquals(50, restoredInventory.get(0)?.count)
        assertEquals("wood", restoredInventory.get(0)?.item?.id)
        assertEquals(25, restoredInventory.get(3)?.count)
        assertEquals("berries", restoredInventory.get(3)?.item?.id)

        // Verify unknown item skipped and warning logged
        assertNull(restoredInventory.get(7))
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("unknown_relic"))
    }

    @Test
    fun testInventoryChangedEventFiredOncePerOperation() {
        val inventory = InventoryLogic(size = 20, hotbarSize = 5)
        var changeCount = 0
        inventory.addListener { changeCount++ }

        // 1. add: 1 event
        inventory.add(wood, 10)
        assertEquals(1, changeCount)

        // 2. move: 1 event
        inventory.move(0, 6)
        assertEquals(2, changeCount)

        // 3. split: 1 event
        inventory.split(6)
        assertEquals(3, changeCount)

        // 4. sort: 1 event
        inventory.sort()
        assertEquals(4, changeCount)

        // 5. remove: 1 event
        inventory.remove(wood, 5)
        assertEquals(5, changeCount)

        // 6. clear: 1 event
        inventory.clear(0) // slot 0 might be null or not
        // Let's set a slot and clear it
        inventory.set(2, ItemStack(stone, 5))
        val current = changeCount
        inventory.clear(2)
        assertEquals(current + 1, changeCount)
    }
}
