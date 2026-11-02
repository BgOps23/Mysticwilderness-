package com.realdeelstudios.mysticwilderness.ecs.components

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic

data class InventoryComponent(
    val inventory: InventoryLogic = InventoryLogic(size = 20, hotbarSize = 5)
) : Component<InventoryComponent> {
    override fun type(): ComponentType<InventoryComponent> = InventoryComponent

    companion object : ComponentType<InventoryComponent>()
}
