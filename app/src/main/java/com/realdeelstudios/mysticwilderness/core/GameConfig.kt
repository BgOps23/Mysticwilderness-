package com.realdeelstudios.mysticwilderness.core

object GameConfig {
    const val TILE_SIZE = 16f
    const val CHUNK_SIZE = 16
    const val FIXED_DELTA_TIME = 1f / 60f
    const val MAX_FRAME_DELTA = 0.25f

    // Targeted virtual screen width for integer pixel scaling (~22 tiles in landscape)
    const val TARGET_VIRTUAL_WIDTH = 352f

    // 4 tiles/second = 64 pixels/second
    const val PLAYER_SPEED = 64f

    // Player collider: ~10x6 px box at the feet, offset from 16x16 sprite
    const val PLAYER_COLLIDER_WIDTH = 10f
    const val PLAYER_COLLIDER_HEIGHT = 6f
    const val PLAYER_COLLIDER_OFFSET_X = 3f
    const val PLAYER_COLLIDER_OFFSET_Y = 1f

    // Input touch controls
    const val JOYSTICK_DEADZONE_RATIO = 0.12f
    const val JOYSTICK_MAX_RADIUS_DP = 56f
    const val ACTION_BUTTON_SIZE_DP = 64f

    // Frame-rate independent camera smoothing decay coefficient
    const val CAMERA_SMOOTH_DECAY = 10f
}
