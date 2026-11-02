package com.realdeelstudios.mysticwilderness.ecs.components

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.realdeelstudios.mysticwilderness.inventory.ItemStack

class DroppedItemTag : Component<DroppedItemTag> {
    override fun type(): ComponentType<DroppedItemTag> = DroppedItemTag

    companion object : ComponentType<DroppedItemTag>()
}

data class ItemStackComponent(
    var stack: ItemStack
) : Component<ItemStackComponent> {
    override fun type(): ComponentType<ItemStackComponent> = ItemStackComponent

    companion object : ComponentType<ItemStackComponent>()
}

data class PickupDelay(
    var remainingSeconds: Float = 1.0f
) : Component<PickupDelay> {
    override fun type(): ComponentType<PickupDelay> = PickupDelay

    companion object : ComponentType<PickupDelay>()
}

data class DroppedItemMetadata(
    val creationOrder: Long,
    var bobTimer: Float = 0f
) : Component<DroppedItemMetadata> {
    override fun type(): ComponentType<DroppedItemMetadata> = DroppedItemMetadata

    companion object : ComponentType<DroppedItemMetadata>()
}
