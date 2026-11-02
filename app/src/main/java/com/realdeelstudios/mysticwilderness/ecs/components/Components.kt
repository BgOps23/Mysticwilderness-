package com.realdeelstudios.mysticwilderness.ecs.components

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

enum class Direction {
    DOWN,
    UP,
    LEFT,
    RIGHT
}

data class Position(
    var x: Float = 0f,
    var y: Float = 0f,
    var prevX: Float = x,
    var prevY: Float = y
) : Component<Position> {
    override fun type(): ComponentType<Position> = Position

    companion object : ComponentType<Position>()
}

data class Velocity(
    var x: Float = 0f,
    var y: Float = 0f
) : Component<Velocity> {
    override fun type(): ComponentType<Velocity> = Velocity

    companion object : ComponentType<Velocity>()
}

data class Collider(
    val width: Float,
    val height: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) : Component<Collider> {
    override fun type(): ComponentType<Collider> = Collider

    companion object : ComponentType<Collider>()
}

data class FacingComponent(
    var direction: Direction = Direction.DOWN
) : Component<FacingComponent> {
    override fun type(): ComponentType<FacingComponent> = FacingComponent

    companion object : ComponentType<FacingComponent>()
}

data class SpriteComponent(
    var regionPrefix: String = "player",
    var zIndex: Int = 0
) : Component<SpriteComponent> {
    override fun type(): ComponentType<SpriteComponent> = SpriteComponent

    companion object : ComponentType<SpriteComponent>()
}

class PlayerTag : Component<PlayerTag> {
    override fun type(): ComponentType<PlayerTag> = PlayerTag

    companion object : ComponentType<PlayerTag>()
}
