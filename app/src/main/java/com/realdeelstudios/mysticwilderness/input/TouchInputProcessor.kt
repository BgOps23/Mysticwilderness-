package com.realdeelstudios.mysticwilderness.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.realdeelstudios.mysticwilderness.core.GameConfig
import kotlin.math.hypot
import kotlin.math.min

class TouchInputProcessor : InputAdapter() {
    val state = InputState()

    var isDebugOverlayEnabled: Boolean = false

    var isModalOpen: Boolean = false
    var onBackpackClicked: (() -> Unit)? = null
    var onGiveStarterItemsClicked: (() -> Unit)? = null

    // Backpack & Starter item button hit areas
    var backpackX = 0f
    var backpackY = 0f
    var backpackSize = 48f
    var giveStarterX = 0f
    var giveStarterY = 0f
    var giveStarterW = 0f
    var giveStarterH = 0f

    private var joystickPointer: Int = -1
    private var actionPointer: Int = -1

    private var density: Float = 1f
    private var maxRadius: Float = 56f
    private var deadZone: Float = 56f * GameConfig.JOYSTICK_DEADZONE_RATIO

    fun updateScreenDimensions(width: Float, height: Float, screenDensity: Float) {
        density = screenDensity
        maxRadius = GameConfig.JOYSTICK_MAX_RADIUS_DP * density
        deadZone = maxRadius * GameConfig.JOYSTICK_DEADZONE_RATIO

        // Action button at bottom-right (minimum 64dp size)
        val actionDiameter = GameConfig.ACTION_BUTTON_SIZE_DP * density
        val margin = 20f * density
        state.actionButtonRadius = actionDiameter / 2f
        state.actionButtonCenterX = width - margin - state.actionButtonRadius
        state.actionButtonCenterY = margin + state.actionButtonRadius

        // Debug toggle button at top-right
        state.debugButtonWidth = 80f * density
        state.debugButtonHeight = 36f * density
        state.debugButtonX = width - state.debugButtonWidth - 16f * density
        state.debugButtonY = height - state.debugButtonHeight - 16f * density

        // Backpack button at top-right next to debug
        backpackSize = (44f * density).coerceIn(44f, 60f)
        backpackY = height - backpackSize - 12f * density
        backpackX = width - (80f * density + 16f * density) - backpackSize - 12f * density

        // Give starter items button
        giveStarterW = 200f * density
        giveStarterH = 34f * density
        giveStarterX = 24f
        giveStarterY = height - 16f - 170f * density
    }

    private fun toUiY(screenY: Int): Float = Gdx.graphics.height.toFloat() - screenY.toFloat()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val touchX = screenX.toFloat()
        val touchY = toUiY(screenY)

        // 1. Check Debug Toggle Button touch
        if (touchX >= state.debugButtonX && touchX <= state.debugButtonX + state.debugButtonWidth &&
            touchY >= state.debugButtonY && touchY <= state.debugButtonY + state.debugButtonHeight
        ) {
            isDebugOverlayEnabled = !isDebugOverlayEnabled
            Gdx.app.log("Input", "Debug overlay toggled: $isDebugOverlayEnabled")
            return true
        }

        // 2. Check Give Starter Items button if debug is enabled
        if (isDebugOverlayEnabled &&
            touchX >= giveStarterX && touchX <= giveStarterX + giveStarterW &&
            touchY >= giveStarterY && touchY <= giveStarterY + giveStarterH
        ) {
            onGiveStarterItemsClicked?.invoke()
            return true
        }

        // 3. Check Backpack Button touch
        if (touchX >= backpackX && touchX <= backpackX + backpackSize &&
            touchY >= backpackY && touchY <= backpackY + backpackSize
        ) {
            onBackpackClicked?.invoke()
            return true
        }

        // 4. If modal is active, suppress gameplay touch controls
        if (isModalOpen) {
            return false
        }

        // 5. Check Action Button touch (bottom-right)
        val distToAction = hypot(touchX - state.actionButtonCenterX, touchY - state.actionButtonCenterY)
        if (distToAction <= state.actionButtonRadius * 1.25f) { // slightly generous touch target
            actionPointer = pointer
            state.isActionPressed = true
            Gdx.app.log("Input", "action pressed")
            return true
        }

        // 6. Floating Virtual Joystick (starts only in left 40% of screen)
        val leftZoneThreshold = Gdx.graphics.width * 0.40f
        if (touchX <= leftZoneThreshold && joystickPointer == -1) {
            joystickPointer = pointer
            state.isJoystickActive = true
            state.joystickOriginX = touchX
            state.joystickOriginY = touchY
            state.joystickCurrentX = touchX
            state.joystickCurrentY = touchY
            state.movementX = 0f
            state.movementY = 0f
            return true
        }

        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val touchX = screenX.toFloat()
        val touchY = toUiY(screenY)

        if (pointer == joystickPointer && state.isJoystickActive) {
            state.joystickCurrentX = touchX
            state.joystickCurrentY = touchY

            val dx = touchX - state.joystickOriginX
            val dy = touchY - state.joystickOriginY
            val dist = hypot(dx, dy)

            if (dist < deadZone) {
                state.movementX = 0f
                state.movementY = 0f
            } else {
                // Analog magnitude normalized past dead zone
                val clampedDist = min(dist, maxRadius)
                val magnitude = (clampedDist - deadZone) / (maxRadius - deadZone)
                val dirX = dx / dist
                val dirY = dy / dist
                state.movementX = dirX * magnitude
                state.movementY = dirY * magnitude
            }
            return true
        }

        if (pointer == actionPointer) {
            val distToAction = hypot(touchX - state.actionButtonCenterX, touchY - state.actionButtonCenterY)
            state.isActionPressed = distToAction <= state.actionButtonRadius * 1.5f
            return true
        }

        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == joystickPointer) {
            joystickPointer = -1
            state.isJoystickActive = false
            state.movementX = 0f
            state.movementY = 0f
            return true
        }

        if (pointer == actionPointer) {
            actionPointer = -1
            state.isActionPressed = false
            return true
        }

        return false
    }
}
