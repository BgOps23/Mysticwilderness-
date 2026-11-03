package com.realdeelstudios.mysticwilderness.data

import kotlinx.serialization.Serializable

@Serializable
enum class DepletionBehavior {
    remove_and_respawn,
    stay_depleted_and_regrow
}

@Serializable
data class NodeColliderDef(
    val width: Float,
    val height: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

@Serializable
data class NodeDropDef(
    val itemId: String,
    val minCount: Int = 1,
    val maxCount: Int = 1,
    val chance: Float = 1.0f
)

@Serializable
data class RespawnTimeDef(
    val min: Float,
    val max: Float
)

@Serializable
data class NodeDef(
    val id: String,
    val name: String,
    val spriteRegion: String,
    val depletedSpriteRegion: String? = null,
    val maxHealth: Int,
    val requiredToolType: String? = null,
    val requiredTier: Int = 0,
    val handDamage: Int = 0,
    val blocksMovement: Boolean = false,
    val colliderSize: NodeColliderDef? = null,
    val sortAnchor: Float = 0f,
    val drops: List<NodeDropDef> = emptyList(),
    val depletionBehavior: DepletionBehavior = DepletionBehavior.remove_and_respawn,
    val respawnGameSeconds: RespawnTimeDef = RespawnTimeDef(720f, 1440f),
    val tags: List<String> = emptyList()
)
