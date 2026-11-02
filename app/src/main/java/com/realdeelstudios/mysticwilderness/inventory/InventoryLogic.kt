package com.realdeelstudios.mysticwilderness.inventory

import com.realdeelstudios.mysticwilderness.data.ItemDef
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import kotlinx.serialization.Serializable
import kotlin.math.min

@Serializable
data class SlotSaveData(
    val slot: Int,
    val itemId: String,
    val count: Int
)

class InventoryLogic(
    val size: Int = 20,
    val hotbarSize: Int = 5
) {
    private val slots: Array<ItemStack?> = Array(size) { null }
    private val listeners = ArrayList<InventoryListener>()

    var selectedHotbarIndex: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, hotbarSize - 1)
            if (field != clamped) {
                field = clamped
                notifyChanged()
            }
        }

    fun addListener(listener: InventoryListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: InventoryListener) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        for (i in 0 until listeners.size) {
            listeners[i].onInventoryChanged(this)
        }
    }

    fun get(slot: Int): ItemStack? {
        if (slot !in 0 until size) return null
        return slots[slot]
    }

    fun set(slot: Int, stack: ItemStack?) {
        if (slot !in 0 until size) return
        slots[slot] = stack
        notifyChanged()
    }

    val selectedHotbarItem: ItemStack?
        get() = get(selectedHotbarIndex)

    /**
     * Adds count of item to inventory.
     * Fills existing partial stacks first, then empty slots.
     * Returns remainder that did not fit.
     * Fires InventoryChanged once if any items were added.
     */
    fun add(item: ItemDef, count: Int): Int {
        if (count <= 0) return 0
        var remaining = count

        // 1. Fill existing stacks of the same item
        for (i in 0 until size) {
            val current = slots[i]
            if (current != null && current.item.id == item.id && !current.isFull) {
                val space = current.spaceLeft
                val toAdd = min(space, remaining)
                current.count += toAdd
                remaining -= toAdd
                if (remaining <= 0) break
            }
        }

        // 2. Put remainder into empty slots
        if (remaining > 0) {
            for (i in 0 until size) {
                if (slots[i] == null) {
                    val toAdd = min(item.maxStack, remaining)
                    slots[i] = ItemStack(item, toAdd)
                    remaining -= toAdd
                    if (remaining <= 0) break
                }
            }
        }

        if (remaining != count) {
            notifyChanged()
        }
        return remaining
    }

    /**
     * Removes count of item across one or more stacks.
     * Returns true if fully removed, false if not enough items existed (inventory left untouched).
     * Fires InventoryChanged once at completion.
     */
    fun remove(item: ItemDef, count: Int): Boolean {
        if (count <= 0) return true
        if (!has(item, count)) return false

        var toRemove = count
        for (i in 0 until size) {
            val current = slots[i]
            if (current != null && current.item.id == item.id) {
                if (current.count <= toRemove) {
                    toRemove -= current.count
                    slots[i] = null
                } else {
                    current.count -= toRemove
                    toRemove = 0
                }
                if (toRemove <= 0) break
            }
        }

        notifyChanged()
        return true
    }

    fun has(item: ItemDef, count: Int): Boolean {
        return countOf(item) >= count
    }

    fun countOf(item: ItemDef): Int {
        var total = 0
        for (i in 0 until size) {
            val current = slots[i]
            if (current != null && current.item.id == item.id) {
                total += current.count
            }
        }
        return total
    }

    /**
     * Moves items between two slots:
     * - If destination is empty: moves stack.
     * - If destination has same item: merges up to maxStack (remainder stays in source).
     * - If destination has different item: swaps slots.
     * Fires InventoryChanged once.
     */
    fun move(from: Int, to: Int) {
        if (from == to) return
        if (from !in 0 until size || to !in 0 until size) return
        val source = slots[from] ?: return
        val dest = slots[to]

        if (dest == null) {
            slots[to] = source
            slots[from] = null
        } else if (dest.item.id == source.item.id) {
            val space = dest.spaceLeft
            if (space > 0) {
                val transfer = min(space, source.count)
                dest.count += transfer
                source.count -= transfer
                if (source.count <= 0) {
                    slots[from] = null
                }
            } else {
                slots[to] = source
                slots[from] = dest
            }
        } else {
            slots[to] = source
            slots[from] = dest
        }

        notifyChanged()
    }

    /**
     * Splits stack in slot in half and places the split half into first empty slot.
     * For odd counts (e.g. 5), ceiling remains in slot (3) and floor moves (2).
     * Fires InventoryChanged once.
     */
    fun split(slot: Int): Boolean {
        if (slot !in 0 until size) return false
        val current = slots[slot] ?: return false
        if (current.count <= 1) return false

        val emptySlot = findFirstEmptySlot() ?: return false

        val toMove = current.count / 2
        val toKeep = current.count - toMove

        current.count = toKeep
        slots[emptySlot] = ItemStack(current.item, toMove)

        notifyChanged()
        return true
    }

    /**
     * Amendment 4: Sort() affects ONLY backpack slots 5-19, NEVER the hotbar (slots 0-4).
     * 1. Collects and merges partial stacks of the same item within slots 5..19.
     * 2. Groups by category (tool -> resource -> consumable -> placeable -> misc), then alphabetically by name.
     * 3. Compacts items into slots 5..19. Remaining backpack slots become null.
     * Fires InventoryChanged once.
     */
    fun sort() {
        val startSlot = hotbarSize // slot 5
        val endSlot = size         // slot 20

        // Collect all non-empty stacks from backpack only (slots 5..19)
        val backpackStacks = ArrayList<ItemStack>()
        for (i in startSlot until endSlot) {
            val s = slots[i]
            if (s != null && s.count > 0) {
                backpackStacks.add(s)
            }
            slots[i] = null
        }

        if (backpackStacks.isEmpty()) return

        // Merge stacks of the same item
        val mergedMap = LinkedHashMap<String, Int>()
        val itemDefs = HashMap<String, ItemDef>()
        for (stack in backpackStacks) {
            val id = stack.item.id
            itemDefs[id] = stack.item
            mergedMap[id] = (mergedMap[id] ?: 0) + stack.count
        }

        // Re-chunk into maxStacks
        val finalStacks = ArrayList<ItemStack>()
        for ((id, totalCount) in mergedMap) {
            val def = itemDefs[id] ?: continue
            var countLeft = totalCount
            while (countLeft > 0) {
                val chunkSize = min(def.maxStack, countLeft)
                finalStacks.add(ItemStack(def, chunkSize))
                countLeft -= chunkSize
            }
        }

        // Sort by Category ordinal, then Name, then descending count
        finalStacks.sortWith(
            compareBy<ItemStack> { it.item.category.ordinal }
                .thenBy { it.item.name }
                .thenByDescending { it.count }
        )

        // Write back strictly to backpack slots 5..19
        for (i in 0 until (endSlot - startSlot)) {
            val slotIdx = startSlot + i
            slots[slotIdx] = if (i < finalStacks.size) finalStacks[i] else null
        }

        notifyChanged()
    }

    fun clear(slot: Int): ItemStack? {
        if (slot !in 0 until size) return null
        val previous = slots[slot]
        slots[slot] = null
        if (previous != null) {
            notifyChanged()
        }
        return previous
    }

    fun clearAll() {
        var changed = false
        for (i in 0 until size) {
            if (slots[i] != null) {
                slots[i] = null
                changed = true
            }
        }
        if (changed) notifyChanged()
    }

    fun findFirstEmptySlot(): Int? {
        for (i in 0 until size) {
            if (slots[i] == null) return i
        }
        return null
    }

    fun isFull(): Boolean = findFirstEmptySlot() == null

    fun toSaveData(): List<SlotSaveData> {
        val list = ArrayList<SlotSaveData>()
        for (i in 0 until size) {
            val s = slots[i]
            if (s != null && s.count > 0) {
                list.add(SlotSaveData(slot = i, itemId = s.item.id, count = s.count))
            }
        }
        return list
    }

    fun fromSaveData(data: List<SlotSaveData>, logWarning: (String) -> Unit = {}) {
        clearAll()
        for (entry in data) {
            if (entry.slot !in 0 until size) continue
            val item = ItemRegistry.get(entry.itemId)
            if (item == null) {
                logWarning("Skipping unknown item ID '${entry.itemId}' in save data for slot ${entry.slot}")
                continue
            }
            val count = entry.count.coerceIn(1, item.maxStack)
            slots[entry.slot] = ItemStack(item, count)
        }
        notifyChanged()
    }
}
