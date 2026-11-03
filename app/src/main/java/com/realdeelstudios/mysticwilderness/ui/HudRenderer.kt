package com.realdeelstudios.mysticwilderness.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.input.IInputState
import kotlin.math.hypot
import kotlin.math.min

class HudRenderer(
    private val atlasProvider: IAtlasProvider,
    private val batch: SpriteBatch
) : Disposable {

    val font = BitmapFont().apply {
        data.scale(1.2f)
    }
    private val glyphLayout = GlyphLayout()
    private val sb = StringBuilder(256)

    // Backpack button bounds
    var backpackBtnX = 0f
        private set
    var backpackBtnY = 0f
        private set
    var backpackBtnSize = 48f
        private set

    // "Give starter items" debug button bounds
    var giveStarterBtnX = 0f
        private set
    var giveStarterBtnY = 0f
        private set
    var giveStarterBtnW = 0f
        private set
    var giveStarterBtnH = 0f
        private set

    // "+1 Day" debug button bounds
    var advanceDayBtnX = 0f
        private set
    var advanceDayBtnY = 0f
        private set
    var advanceDayBtnW = 0f
        private set
    var advanceDayBtnH = 0f
        private set

    // Floating alert banner message
    var floatingMessageText: String = ""
        private set
    var floatingMessageTimer: Float = 0f
        private set

    fun triggerInventoryFullAlert() {
        showFloatingMessage("Inventory full!")
    }

    fun showFloatingMessage(message: String) {
        floatingMessageText = message
        floatingMessageTimer = 2.0f
    }

    fun updateScreenDimensions(width: Float, height: Float, density: Float) {
        val targetScale = (density * 0.9f).coerceIn(1.0f, 2.5f)
        font.data.setScale(targetScale)

        // Backpack button at top-right, to the left of the debug toggle
        backpackBtnSize = (44f * density).coerceIn(44f, 60f)
        backpackBtnY = height - backpackBtnSize - 12f * density
        backpackBtnX = width - (80f * density + 16f * density) - backpackBtnSize - 12f * density

        // Give starter items button inside debug card
        giveStarterBtnW = 200f * (font.data.scaleX / 1.2f)
        giveStarterBtnH = 28f * (font.data.scaleY / 1.2f)
        giveStarterBtnX = 24f
        giveStarterBtnY = height - 16f - 195f * (font.data.scaleY / 1.2f)

        // Advance day button inside debug card
        advanceDayBtnW = 200f * (font.data.scaleX / 1.2f)
        advanceDayBtnH = 28f * (font.data.scaleY / 1.2f)
        advanceDayBtnX = 24f
        advanceDayBtnY = giveStarterBtnY - advanceDayBtnH - 6f * (font.data.scaleY / 1.2f)
    }

    fun render(
        inputState: IInputState,
        isDebugEnabled: Boolean,
        playerTileX: Int,
        playerTileY: Int,
        chunksRendered: Int,
        totalChunks: Int,
        entityCount: Int,
        visibleNodes: Int,
        totalNodes: Int,
        clockTimeFormatted: String,
        delta: Float
    ) {
        if (floatingMessageTimer > 0f) {
            floatingMessageTimer -= delta
        }

        // 1. Draw Floating Virtual Joystick if active
        if (inputState.isJoystickActive) {
            val baseRegion = atlasProvider.getRegion("ui_joystick_base")
            val knobRegion = atlasProvider.getRegion("ui_joystick_knob")

            val baseRadius = 56f * Gdx.graphics.density
            val knobRadius = 24f * Gdx.graphics.density

            batch.setColor(1f, 1f, 1f, 0.85f)
            batch.draw(
                baseRegion,
                inputState.joystickOriginX - baseRadius,
                inputState.joystickOriginY - baseRadius,
                baseRadius * 2f,
                baseRadius * 2f
            )

            val dx = inputState.joystickCurrentX - inputState.joystickOriginX
            val dy = inputState.joystickCurrentY - inputState.joystickOriginY
            val dist = hypot(dx, dy)
            val clampedDist = min(dist, baseRadius)
            val knobX = if (dist > 0.001f) inputState.joystickOriginX + (dx / dist) * clampedDist else inputState.joystickOriginX
            val knobY = if (dist > 0.001f) inputState.joystickOriginY + (dy / dist) * clampedDist else inputState.joystickOriginY

            batch.draw(
                knobRegion,
                knobX - knobRadius,
                knobY - knobRadius,
                knobRadius * 2f,
                knobRadius * 2f
            )
        }

        // 2. Draw Fixed Action Button (>= 64dp touch target)
        val actionRegionName = if (inputState.isActionPressed) "ui_action_button_pressed" else "ui_action_button"
        val actionRegion = atlasProvider.getRegion(actionRegionName)
        val r = inputState.actionButtonRadius
        batch.setColor(1f, 1f, 1f, 0.95f)
        batch.draw(
            actionRegion,
            inputState.actionButtonCenterX - r,
            inputState.actionButtonCenterY - r,
            r * 2f,
            r * 2f
        )

        // 3. Draw Backpack Button on HUD
        val backpackIcon = atlasProvider.getRegion("ui_backpack_icon")
        val slotBg = atlasProvider.getRegion("ui_slot_bg")
        batch.setColor(1f, 1f, 1f, 0.85f)
        batch.draw(slotBg, backpackBtnX, backpackBtnY, backpackBtnSize, backpackBtnSize)
        val pad = backpackBtnSize * 0.12f
        batch.draw(backpackIcon, backpackBtnX + pad, backpackBtnY + pad, backpackBtnSize - 2 * pad, backpackBtnSize - 2 * pad)

        // 4. Draw Debug Toggle Button at top-right
        val whitePixel = atlasProvider.getRegion("white_pixel")
        batch.setColor(0.1f, 0.15f, 0.2f, 0.75f)
        batch.draw(
            whitePixel,
            inputState.debugButtonX,
            inputState.debugButtonY,
            inputState.debugButtonWidth,
            inputState.debugButtonHeight
        )
        val borderCol = if (isDebugEnabled) Color.GREEN else Color.LIGHT_GRAY
        batch.setColor(borderCol)
        val borderT = 2f
        batch.draw(whitePixel, inputState.debugButtonX, inputState.debugButtonY, inputState.debugButtonWidth, borderT)
        batch.draw(whitePixel, inputState.debugButtonX, inputState.debugButtonY + inputState.debugButtonHeight - borderT, inputState.debugButtonWidth, borderT)
        batch.draw(whitePixel, inputState.debugButtonX, inputState.debugButtonY, borderT, inputState.debugButtonHeight)
        batch.draw(whitePixel, inputState.debugButtonX + inputState.debugButtonWidth - borderT, inputState.debugButtonY, borderT, inputState.debugButtonHeight)

        val label = if (isDebugEnabled) "DBG: ON" else "DBG: OFF"
        glyphLayout.setText(font, label)
        val labelX = inputState.debugButtonX + (inputState.debugButtonWidth - glyphLayout.width) / 2f
        val labelY = inputState.debugButtonY + (inputState.debugButtonHeight + glyphLayout.height) / 2f
        font.color = if (isDebugEnabled) Color.WHITE else Color.LIGHT_GRAY
        font.draw(batch, label, labelX, labelY)

        // 5. Draw Debug Overlay Telemetry when active
        if (isDebugEnabled) {
            renderDebugOverlay(
                playerTileX = playerTileX,
                playerTileY = playerTileY,
                chunksRendered = chunksRendered,
                totalChunks = totalChunks,
                entityCount = entityCount,
                visibleNodes = visibleNodes,
                totalNodes = totalNodes,
                clockTimeFormatted = clockTimeFormatted
            )
        }

        // 6. Draw Floating alert banner if active
        if (floatingMessageTimer > 0f && floatingMessageText.isNotBlank()) {
            glyphLayout.setText(font, floatingMessageText)
            val alertW = (glyphLayout.width + 32f).coerceAtLeast(180f)
            val alertH = 38f * (font.data.scaleY / 1.2f)
            val alertX = (Gdx.graphics.width - alertW) / 2f
            val alertY = Gdx.graphics.height - 50f * Gdx.graphics.density

            val alpha = (floatingMessageTimer / 0.5f).coerceIn(0f, 1f)
            batch.setColor(0.85f, 0.20f, 0.20f, 0.90f * alpha)
            batch.draw(whitePixel, alertX, alertY, alertW, alertH)
            batch.setColor(Color.WHITE)
            batch.draw(whitePixel, alertX, alertY, alertW, 2f)
            batch.draw(whitePixel, alertX, alertY + alertH - 2f, alertW, 2f)

            font.color = Color.WHITE
            val textX = alertX + (alertW - glyphLayout.width) / 2f
            val textY = alertY + (alertH + glyphLayout.height) / 2f
            font.draw(batch, floatingMessageText, textX, textY)
        }

        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun renderDebugOverlay(
        playerTileX: Int,
        playerTileY: Int,
        chunksRendered: Int,
        totalChunks: Int,
        entityCount: Int,
        visibleNodes: Int,
        totalNodes: Int,
        clockTimeFormatted: String
    ) {
        val whitePixel = atlasProvider.getRegion("white_pixel")
        val margin = 16f
        val cardWidth = 280f * (font.data.scaleX / 1.2f)
        val cardHeight = 240f * (font.data.scaleY / 1.2f)
        val cardX = margin
        val cardY = Gdx.graphics.height - margin - cardHeight

        // Dark translucent card background
        batch.setColor(0.05f, 0.08f, 0.12f, 0.88f)
        batch.draw(whitePixel, cardX, cardY, cardWidth, cardHeight)

        // Emerald accent header bar
        batch.setColor(0.18f, 0.80f, 0.44f, 0.9f)
        batch.draw(whitePixel, cardX, cardY + cardHeight - 4f, cardWidth, 4f)

        // Telemetry lines
        val fps = Gdx.graphics.framesPerSecond
        val frameMs = (Gdx.graphics.deltaTime * 1000f).toInt()
        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalHeapMb = runtime.totalMemory() / (1024 * 1024)

        font.color = Color.WHITE
        var textY = cardY + cardHeight - 14f

        // Line 1: FPS & Frame Time (60 FPS verification)
        sb.setLength(0)
        sb.append("FPS: ").append(fps).append(" (").append(frameMs).append(" ms)")
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 2: Game Clock
        sb.setLength(0)
        sb.append("Clock: ").append(clockTimeFormatted)
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 3: Player Tile Position
        sb.setLength(0)
        sb.append("Player Tile: (").append(playerTileX).append(", ").append(playerTileY).append(")")
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 4: Chunks Rendered
        sb.setLength(0)
        sb.append("Chunks: ").append(chunksRendered).append(" / ").append(totalChunks).append(" visible")
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 5: Visible Nodes count (Amendment 6)
        sb.setLength(0)
        sb.append("Nodes Visible: ").append(visibleNodes).append(" / ").append(totalNodes)
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 6: Total Entities count
        sb.setLength(0)
        sb.append("Entities: ").append(entityCount)
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 18f * (font.data.scaleY / 1.2f)

        // Line 7: Heap usage
        sb.setLength(0)
        sb.append("JVM Heap: ").append(usedHeapMb).append(" MB / ").append(totalHeapMb).append(" MB")
        font.draw(batch, sb, cardX + 12f, textY)
        textY -= 24f * (font.data.scaleY / 1.2f)

        // Button: Give Starter Items
        batch.setColor(0.18f, 0.70f, 0.40f, 0.90f)
        batch.draw(whitePixel, giveStarterBtnX, giveStarterBtnY, giveStarterBtnW, giveStarterBtnH)
        font.color = Color.WHITE
        font.draw(batch, "+ Give Starter Items", giveStarterBtnX + 10f, giveStarterBtnY + giveStarterBtnH - 7f)

        // Button: Advance Day (+1 Day)
        batch.setColor(0.20f, 0.55f, 0.85f, 0.90f)
        batch.draw(whitePixel, advanceDayBtnX, advanceDayBtnY, advanceDayBtnW, advanceDayBtnH)
        font.color = Color.WHITE
        font.draw(batch, "+ 1 Day (Advance Clock)", advanceDayBtnX + 10f, advanceDayBtnY + advanceDayBtnH - 7f)
    }

    override fun dispose() {
        font.dispose()
    }
}
