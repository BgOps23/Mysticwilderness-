package com.realdeelstudios.mysticwilderness.input

interface IInputState {
    val isJoystickActive: Boolean
    val joystickOriginX: Float
    val joystickOriginY: Float
    val joystickCurrentX: Float
    val joystickCurrentY: Float
    val movementX: Float
    val movementY: Float
    val isActionPressed: Boolean
    val actionButtonCenterX: Float
    val actionButtonCenterY: Float
    val actionButtonRadius: Float
    val debugButtonX: Float
    val debugButtonY: Float
    val debugButtonWidth: Float
    val debugButtonHeight: Float
}

class InputState : IInputState {
    override var isJoystickActive: Boolean = false
    override var joystickOriginX: Float = 0f
    override var joystickOriginY: Float = 0f
    override var joystickCurrentX: Float = 0f
    override var joystickCurrentY: Float = 0f
    override var movementX: Float = 0f
    override var movementY: Float = 0f
    override var isActionPressed: Boolean = false
    override var actionButtonCenterX: Float = 0f
    override var actionButtonCenterY: Float = 0f
    override var actionButtonRadius: Float = 0f
    override var debugButtonX: Float = 0f
    override var debugButtonY: Float = 0f
    override var debugButtonWidth: Float = 0f
    override var debugButtonHeight: Float = 0f
}
