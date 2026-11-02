package com.realdeelstudios.mysticwilderness.data

import kotlinx.serialization.Serializable

@Serializable
enum class ItemCategory {
    tool,
    resource,
    consumable,
    placeable,
    misc;

    val displayName: String
        get() = name.replaceFirstChar { it.uppercase() }
}

@Serializable
data class ToolData(
    val type: String, // "axe", "pickaxe"
    val tier: Int = 1,
    val power: Float = 1.0f
)

@Serializable
data class StatEffect(
    val stat: String, // "health", "energy", etc.
    val amount: Float
)

@Serializable
data class ConsumableData(
    val effects: List<StatEffect> = emptyList()
)

@Serializable
data class PlaceableData(
    val structureId: String
)

@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val description: String = "",
    val category: ItemCategory = ItemCategory.misc,
    val maxStack: Int = 99,
    val baseValue: Int = 0,
    val iconRegion: String,
    val tags: List<String> = emptyList(),
    val tool: ToolData? = null,
    val consumable: ConsumableData? = null,
    val placeable: PlaceableData? = null
)
