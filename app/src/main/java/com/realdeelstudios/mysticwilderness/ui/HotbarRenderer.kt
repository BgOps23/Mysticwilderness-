package com.realdeelstudios.mysticwilderness.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic

class HotbarRenderer(
    private val atlasProvider: IAtlasProvider,
    private val batch: SpriteBatch,
    private val font: BitmapFont
) {
    private val glyphLayout = GlyphLayout()

    var slotSize: Float = 52f
        private set
    var slotSpacing: Float = 8f
        private set
    var startX: Float = 0f
        private set
    var startY: Float = 16f
        private set

    fun updateLayout(screenWidth: Float, density: Float) {
        slotSize = (48f * density).coerceIn(48f, 72f)
        slotSpacing = (6f * density).coerceIn(6f, 12f)
        val totalWidth = 5 * slotSize + 4 * slotSpacing
        startX = (screenWidth - totalWidth) / 2f
        startY = 14f * density
    }

    fun handleTouchDown(touchX: Float, touchY: Float, inventory: InventoryLogic): Boolean {
        for (i in 0 until inventory.hotbarSize) {
            val sx = startX + i * (slotSize + slotSpacing)
            val sy = startY
            if (touchX in sx..(sx + slotSize) && touchY in sy..(sy + slotSize)) {
                inventory.selectedHotbarIndex = i
                return true
            }
        }
        return false
    }

    fun render(inventory: InventoryLogic) {
        val slotBg = atlasProvider.getRegion("ui_slot_bg")
        val slotSelected = atlasProvider.getRegion("ui_slot_selected")

        for (i in 0 until inventory.hotbarSize) {
            val sx = startX + i * (slotSize + slotSpacing)
            val sy = startY
            val isSelected = (i == inventory.selectedHotbarIndex)

            // Draw slot background
            batch.setColor(1f, 1f, 1f, 0.95f)
            batch.draw(slotBg, sx, sy, slotSize, slotSize)

            // Draw item if present
            val stack = inventory.get(i)
            if (stack != null && stack.count > 0) {
                val icon = atlasProvider.getRegion(stack.item.iconRegion)
                val iconPadding = slotSize * 0.15f
                val iconSize = slotSize - 2 * iconPadding
                batch.draw(icon, sx + iconPadding, sy + iconPadding, iconSize, iconSize)

                // Draw count if > 1
                if (stack.count > 1) {
                    val countStr = stack.count.toString()
                    glyphLayout.setText(font, countStr)
                    val countX = sx + slotSize - glyphLayout.width - 4f
                    val countY = sy + glyphLayout.height + 4f
                    font.color = Color.WHITE
                    font.draw(batch, countStr, countX, countY)
                }
            }

            // Draw selection highlight over slot
            if (isSelected) {
                batch.setColor(1f, 1f, 1f, 1f)
                batch.draw(slotSelected, sx - 2f, sy - 2f, slotSize + 4f, slotSize + 4f)
            }
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }
}
