package com.realdeelstudios.mysticwilderness.ecs.components

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import kotlinx.serialization.Serializable

@Serializable
enum class NodeState {
    HEALTHY,
    DEPLETED,
    DEPLETED_HIDDEN
}

@Serializable
data class NodeSaveData(
    val entityX: Float,
    val entityY: Float,
    val nodeDefId: String,
    val health: Int,
    val state: NodeState,
    val respawnAtGameSeconds: Double
)

data class ResourceNodeComponent(
    val nodeDefId: String,
    var currentHealth: Int,
    var maxHealth: Int,
    var state: NodeState = NodeState.HEALTHY,
    var respawnAtGameSeconds: Double = 0.0,
    var hitShakeTimer: Float = 0f,
    var damageFlashTimer: Float = 0f,
    var showHealthBarTimer: Float = 0f
) : Component<ResourceNodeComponent> {
    override fun type(): ComponentType<ResourceNodeComponent> = ResourceNodeComponent

    companion object : ComponentType<ResourceNodeComponent>()
}

class TargetableNodeTag : Component<TargetableNodeTag> {
    override fun type(): ComponentType<TargetableNodeTag> = TargetableNodeTag

    companion object : ComponentType<TargetableNodeTag>()
}
