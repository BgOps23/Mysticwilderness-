package com.realdeelstudios.mysticwilderness.ecs.systems

import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.Direction
import com.realdeelstudios.mysticwilderness.ecs.components.FacingComponent
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.Velocity
import com.realdeelstudios.mysticwilderness.input.IInputState
import com.realdeelstudios.mysticwilderness.world.GameMap
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Family
import com.github.quillraven.fleks.IteratingSystem
import kotlin.math.abs
import kotlin.math.floor

class InputSystem(
    private val inputState: IInputState,
    family: Family
) : IteratingSystem(family) {
    override fun onTickEntity(entity: Entity) {
        val vel = entity[Velocity]
        vel.x = inputState.movementX * GameConfig.PLAYER_SPEED
        vel.y = inputState.movementY * GameConfig.PLAYER_SPEED
    }
}

class MovementSystem(
    family: Family
) : IteratingSystem(family) {
    override fun onTickEntity(entity: Entity) {
        val pos = entity[Position]
        // Store previous coordinates for render interpolation
        pos.prevX = pos.x
        pos.prevY = pos.y
    }
}

class CollisionSystem(
    private val gameMap: GameMap,
    family: Family
) : IteratingSystem(family) {

    override fun onTickEntity(entity: Entity) {
        val pos = entity[Position]
        val vel = entity[Velocity]
        val collider = entity[Collider]
        val dt = GameConfig.FIXED_DELTA_TIME
        val tileSize = GameConfig.TILE_SIZE

        // 1. Resolve X axis
        if (vel.x != 0f) {
            var candX = pos.x + vel.x * dt
            val boxMinX = candX + collider.offsetX
            val boxMaxX = boxMinX + collider.width
            val boxMinY = pos.y + collider.offsetY
            val boxMaxY = boxMinY + collider.height

            val startTileX = floor(boxMinX / tileSize).toInt()
            val endTileX = floor((boxMaxX - 0.001f) / tileSize).toInt()
            val startTileY = floor(boxMinY / tileSize).toInt()
            val endTileY = floor((boxMaxY - 0.001f) / tileSize).toInt()

            var collidedX = false
            for (ty in startTileY..endTileY) {
                for (tx in startTileX..endTileX) {
                    if (gameMap.isTileSolid(tx, ty)) {
                        collidedX = true
                        if (vel.x > 0f) {
                            candX = tx * tileSize - collider.width - collider.offsetX
                        } else if (vel.x < 0f) {
                            candX = (tx + 1) * tileSize - collider.offsetX
                        }
                        vel.x = 0f
                        break
                    }
                }
                if (collidedX) break
            }
            // Clamp within world bounds
            candX = candX.coerceIn(0f, gameMap.widthInPx - collider.width - collider.offsetX)
            pos.x = candX
        }

        // 2. Resolve Y axis
        if (vel.y != 0f) {
            var candY = pos.y + vel.y * dt
            val boxMinX = pos.x + collider.offsetX
            val boxMaxX = boxMinX + collider.width
            val boxMinY = candY + collider.offsetY
            val boxMaxY = boxMinY + collider.height

            val startTileX = floor(boxMinX / tileSize).toInt()
            val endTileX = floor((boxMaxX - 0.001f) / tileSize).toInt()
            val startTileY = floor(boxMinY / tileSize).toInt()
            val endTileY = floor((boxMaxY - 0.001f) / tileSize).toInt()

            var collidedY = false
            for (ty in startTileY..endTileY) {
                for (tx in startTileX..endTileX) {
                    if (gameMap.isTileSolid(tx, ty)) {
                        collidedY = true
                        if (vel.y > 0f) {
                            candY = ty * tileSize - collider.height - collider.offsetY
                        } else if (vel.y < 0f) {
                            candY = (ty + 1) * tileSize - collider.offsetY
                        }
                        vel.y = 0f
                        break
                    }
                }
                if (collidedY) break
            }
            // Clamp within world bounds
            candY = candY.coerceIn(0f, gameMap.heightInPx - collider.height - collider.offsetY)
            pos.y = candY
        }
    }
}

class FacingSystem(
    family: Family
) : IteratingSystem(family) {
    override fun onTickEntity(entity: Entity) {
        val vel = entity[Velocity]
        val facing = entity[FacingComponent]

        if (abs(vel.x) > 0.1f || abs(vel.y) > 0.1f) {
            facing.direction = if (abs(vel.x) > abs(vel.y)) {
                if (vel.x > 0f) Direction.RIGHT else Direction.LEFT
            } else {
                if (vel.y > 0f) Direction.UP else Direction.DOWN
            }
        }
    }
}
