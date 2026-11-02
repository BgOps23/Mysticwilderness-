package com.realdeelstudios.mysticwilderness.inventory

fun interface InventoryListener {
    fun onInventoryChanged(inventory: InventoryLogic)
}
