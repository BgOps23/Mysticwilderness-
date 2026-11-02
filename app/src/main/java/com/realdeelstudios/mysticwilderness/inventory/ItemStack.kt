package com.realdeelstudios.mysticwilderness.inventory

import com.realdeelstudios.mysticwilderness.data.ItemDef
import kotlin.math.max
import kotlin.math.min

data class ItemStack(
    val item: ItemDef,
    var count: Int
) {
    init {
        count = max(0, count)
    }

    val isFull: Boolean
        get() = count >= item.maxStack

    val spaceLeft: Int
        get() = max(0, item.maxStack - count)

    fun copy(count: Int = this.count): ItemStack = ItemStack(item, count)
}
