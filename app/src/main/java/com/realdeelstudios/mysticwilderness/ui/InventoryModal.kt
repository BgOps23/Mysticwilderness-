package com.realdeelstudios.mysticwilderness.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import com.realdeelstudios.mysticwilderness.inventory.ItemStack
import kotlin.math.hypot
import kotlin.math.min

class InventoryModal(
    private val atlasProvider: IAtlasProvider,
    private val batch: SpriteBatch,
    private val font: BitmapFont,
    private val onDropItem: (ItemStack) -> Unit
) {
    var isOpen: Boolean = false
        private set

    var onClose: (() -> Unit)? = null

    var selectedSlot: Int? = null

    // Layout dimensions
    private var modalX = 0f
    private var modalY = 0f
    private var modalW = 0f
    private var modalH = 0f

    private var slotSize = 48f
    private var slotSpacing = 6f
    private var gridStartX = 0f
    private var gridStartY = 0f

    // Detail card bounds
    private var cardX = 0f
    private var cardY = 0f
    private var cardW = 0f
    private var cardH = 0f

    // Action button rectangles inside detail card
    private var btnUseX = 0f; private var btnUseY = 0f; private var btnUseW = 0f; private var btnUseH = 0f
    private var btnSplitX = 0f; private var btnSplitY = 0f; private var btnSplitW = 0f; private var btnSplitH = 0f
    private var btnDropOneX = 0f; private var btnDropOneY = 0f; private var btnDropOneW = 0f; private var btnDropOneH = 0f
    private var btnDropAllX = 0f; private var btnDropAllY = 0f; private var btnDropAllW = 0f; private var btnDropAllH = 0f

    // Header buttons
    private var btnSortX = 0f; private var btnSortY = 0f; private var btnSortW = 0f; private var btnSortH = 0f
    private var btnCloseX = 0f; private var btnCloseY = 0f; private var btnCloseW = 0f; private var btnCloseH = 0f

    // Drag-and-drop state
    private var isDragging = false
    private var dragFromSlot: Int? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var currentDragX = 0f
    private var currentDragY = 0f
    private val dragThreshold = 10f * Gdx.graphics.density

    private val glyphLayout = GlyphLayout()

    fun open() {
        isOpen = true
        selectedSlot = null
        isDragging = false
    }

    fun close() {
        val wasOpen = isOpen
        isOpen = false
        selectedSlot = null
        isDragging = false
        if (wasOpen) {
            onClose?.invoke()
        }
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun updateLayout(screenWidth: Float, screenHeight: Float, density: Float) {
        modalW = (screenWidth * 0.90f).coerceIn(400f, 960f)
        modalH = (screenHeight * 0.88f).coerceIn(240f, 600f)
        modalX = (screenWidth - modalW) / 2f
        modalY = (screenHeight - modalH) / 2f

        // Grid (left 55% of modal)
        val leftAreaW = modalW * 0.55f
        slotSize = (48f * density).coerceIn(44f, 64f)
        slotSpacing = (6f * density).coerceIn(4f, 10f)

        val totalGridW = 5 * slotSize + 4 * slotSpacing
        val totalGridH = 4 * slotSize + 3 * slotSpacing

        gridStartX = modalX + (leftAreaW - totalGridW) / 2f + 10f
        gridStartY = modalY + (modalH - totalGridH) / 2f - 10f

        // Detail card (right 42% of modal)
        cardX = modalX + leftAreaW + 10f
        cardY = modalY + 20f
        cardW = modalW - leftAreaW - 30f
        cardH = modalH - 40f

        // Header buttons
        btnCloseW = 38f * density
        btnCloseH = 34f * density
        btnCloseX = modalX + modalW - btnCloseW - 12f
        btnCloseY = modalY + modalH - btnCloseH - 10f

        btnSortW = 75f * density
        btnSortH = 34f * density
        btnSortX = btnCloseX - btnSortW - 10f
        btnSortY = btnCloseY

        // Detail card action buttons
        val btnPadding = 6f * density
        val btnH = (32f * density).coerceIn(30f, 44f)
        val halfBtnW = (cardW - 3 * btnPadding) / 2f

        btnUseX = cardX + btnPadding
        btnUseY = cardY + 12f + btnH + btnPadding
        btnUseW = halfBtnW
        btnUseH = btnH

        btnSplitX = cardX + 2 * btnPadding + halfBtnW
        btnSplitY = btnUseY
        btnSplitW = halfBtnW
        btnSplitH = btnH

        btnDropOneX = cardX + btnPadding
        btnDropOneY = cardY + 12f
        btnDropOneW = halfBtnW
        btnDropOneH = btnH

        btnDropAllX = cardX + 2 * btnPadding + halfBtnW
        btnDropAllY = cardY + 12f
        btnDropAllW = halfBtnW
        btnDropAllH = btnH
    }

    fun getSlotAt(touchX: Float, touchY: Float): Int? {
        for (r in 0 until 4) { // rows 0..3 (row 0 is hotbar at top or bottom)
            // Display row 0 (hotbar) at top of grid for intuitive visibility
            val slotY = gridStartY + (3 - r) * (slotSize + slotSpacing)
            for (c in 0 until 5) {
                val slotX = gridStartX + c * (slotSize + slotSpacing)
                val slotIndex = r * 5 + c
                if (touchX in slotX..(slotX + slotSize) && touchY in slotY..(slotY + slotSize)) {
                    return slotIndex
                }
            }
        }
        return null
    }

    fun handleTouchDown(touchX: Float, touchY: Float, inventory: InventoryLogic): Boolean {
        if (!isOpen) return false

        // 1. Outside modal tap closes modal
        if (touchX !in modalX..(modalX + modalW) || touchY !in modalY..(modalY + modalH)) {
            close()
            return true
        }

        // 2. Close button
        if (touchX in btnCloseX..(btnCloseX + btnCloseW) && touchY in btnCloseY..(btnCloseY + btnCloseH)) {
            close()
            return true
        }

        // 3. Sort button
        if (touchX in btnSortX..(btnSortX + btnSortW) && touchY in btnSortY..(btnSortY + btnSortH)) {
            inventory.sort()
            selectedSlot = null
            return true
        }

        // 4. Detail card action buttons
        val sel = selectedSlot
        if (sel != null && inventory.get(sel) != null) {
            val stack = inventory.get(sel)!!

            // USE / EQUIP button
            if (touchX in btnUseX..(btnUseX + btnUseW) && touchY in btnUseY..(btnUseY + btnUseH)) {
                Gdx.app.log("Inventory", "Used/Equipped: ${stack.item.name} (${stack.item.category})")
                return true
            }

            // SPLIT button
            if (touchX in btnSplitX..(btnSplitX + btnSplitW) && touchY in btnSplitY..(btnSplitY + btnSplitH)) {
                inventory.split(sel)
                return true
            }

            // DROP ONE button
            if (touchX in btnDropOneX..(btnDropOneX + btnDropOneW) && touchY in btnDropOneY..(btnDropOneY + btnDropOneH)) {
                if (stack.count > 1) {
                    stack.count -= 1
                    inventory.notifyChanged()
                    onDropItem(ItemStack(stack.item, 1))
                } else {
                    inventory.clear(sel)
                    onDropItem(stack)
                    selectedSlot = null
                }
                return true
            }

            // DROP ALL button
            if (touchX in btnDropAllX..(btnDropAllX + btnDropAllW) && touchY in btnDropAllY..(btnDropAllY + btnDropAllH)) {
                val dropped = inventory.clear(sel)
                if (dropped != null) {
                    onDropItem(dropped)
                }
                selectedSlot = null
                return true
            }
        }

        // 5. Grid slots touch: prepare for drag or tap
        val touchedSlot = getSlotAt(touchX, touchY)
        if (touchedSlot != null) {
            dragStartX = touchX
            dragStartY = touchY
            currentDragX = touchX
            currentDragY = touchY
            dragFromSlot = touchedSlot
            isDragging = false
            return true
        }

        return true // Consumes all touches inside modal
    }

    fun handleTouchDragged(touchX: Float, touchY: Float, inventory: InventoryLogic): Boolean {
        if (!isOpen) return false
        val from = dragFromSlot ?: return true

        currentDragX = touchX
        currentDragY = touchY

        if (!isDragging) {
            val dist = hypot(touchX - dragStartX, touchY - dragStartY)
            if (dist > dragThreshold && inventory.get(from) != null) {
                isDragging = true
            }
        }
        return true
    }

    fun handleTouchUp(touchX: Float, touchY: Float, inventory: InventoryLogic): Boolean {
        if (!isOpen) return false
        val from = dragFromSlot

        if (from != null) {
            if (isDragging) {
                // Drop gesture complete: move/merge/swap via drag
                val toSlot = getSlotAt(touchX, touchY)
                if (toSlot != null && toSlot != from) {
                    inventory.move(from, toSlot)
                    selectedSlot = toSlot
                }
                isDragging = false
            } else {
                // Short tap gesture on slot: select slot to show details only (no tap-to-move)
                val target = getSlotAt(touchX, touchY)
                if (target != null) {
                    selectedSlot = target
                }
            }
        }

        dragFromSlot = null
        isDragging = false
        return true
    }

    fun render(inventory: InventoryLogic) {
        if (!isOpen) return

        val whitePixel = atlasProvider.getRegion("white_pixel")
        val slotBg = atlasProvider.getRegion("ui_slot_bg")
        val slotSelected = atlasProvider.getRegion("ui_slot_selected")

        // 1. Semi-transparent backdrop overlay
        batch.setColor(0f, 0f, 0f, 0.70f)
        batch.draw(whitePixel, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // 2. Main Modal Card (Cozy dark slate with emerald trim)
        batch.setColor(0.08f, 0.12f, 0.16f, 0.95f)
        batch.draw(whitePixel, modalX, modalY, modalW, modalH)
        // Accent border
        batch.setColor(0.18f, 0.80f, 0.44f, 0.90f)
        batch.draw(whitePixel, modalX, modalY + modalH - 4f, modalW, 4f)

        // 3. Header: Title, Sort button, Close button
        font.color = Color.WHITE
        font.draw(batch, "INVENTORY", modalX + 20f, modalY + modalH - 16f)

        // Sort button
        batch.setColor(0.20f, 0.30f, 0.40f, 0.85f)
        batch.draw(whitePixel, btnSortX, btnSortY, btnSortW, btnSortH)
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "SORT", btnSortX + 16f, btnSortY + btnSortH - 10f)

        // Close button
        batch.setColor(0.70f, 0.20f, 0.20f, 0.85f)
        batch.draw(whitePixel, btnCloseX, btnCloseY, btnCloseW, btnCloseH)
        font.color = Color.WHITE
        font.draw(batch, "X", btnCloseX + 12f, btnCloseY + btnCloseH - 10f)

        // 4. Left Pane: 5x4 Grid
        for (r in 0 until 4) {
            val slotY = gridStartY + (3 - r) * (slotSize + slotSpacing)
            for (c in 0 until 5) {
                val slotX = gridStartX + c * (slotSize + slotSpacing)
                val slotIndex = r * 5 + c
                val isSel = (slotIndex == selectedSlot)

                // Background
                batch.setColor(1f, 1f, 1f, 0.95f)
                batch.draw(slotBg, slotX, slotY, slotSize, slotSize)

                // Hotbar row tag for row 0
                if (r == 0) {
                    batch.setColor(0.18f, 0.80f, 0.44f, 0.4f)
                    batch.draw(whitePixel, slotX + 2f, slotY + slotSize - 4f, slotSize - 4f, 2f)
                }

                // Item icon
                val stack = inventory.get(slotIndex)
                if (stack != null && stack.count > 0) {
                    // If being dragged, dim the slot item
                    val isBeingDragged = (isDragging && dragFromSlot == slotIndex)
                    val alpha = if (isBeingDragged) 0.3f else 1.0f
                    batch.setColor(1f, 1f, 1f, alpha)

                    val icon = atlasProvider.getRegion(stack.item.iconRegion)
                    val pad = slotSize * 0.15f
                    batch.draw(icon, slotX + pad, slotY + pad, slotSize - 2 * pad, slotSize - 2 * pad)

                    // Stack count
                    if (stack.count > 1) {
                        val countStr = stack.count.toString()
                        glyphLayout.setText(font, countStr)
                        font.color = Color.WHITE
                        font.draw(batch, countStr, slotX + slotSize - glyphLayout.width - 4f, slotY + glyphLayout.height + 4f)
                    }
                }

                // Highlight selected slot
                if (isSel) {
                    batch.setColor(1f, 1f, 1f, 1f)
                    batch.draw(slotSelected, slotX - 2f, slotY - 2f, slotSize + 4f, slotSize + 4f)
                }
            }
        }

        // 5. Right Pane: Detail Card
        batch.setColor(0.12f, 0.18f, 0.24f, 0.90f)
        batch.draw(whitePixel, cardX, cardY, cardW, cardH)
        batch.setColor(0.25f, 0.35f, 0.45f, 0.60f)
        batch.draw(whitePixel, cardX, cardY, cardW, 2f)
        batch.draw(whitePixel, cardX, cardY + cardH - 2f, cardW, 2f)
        batch.draw(whitePixel, cardX, cardY, 2f, cardH)
        batch.draw(whitePixel, cardX + cardW - 2f, cardY, 2f, cardH)

        val selIndex = selectedSlot
        val selStack = if (selIndex != null) inventory.get(selIndex) else null

        if (selStack != null) {
            val item = selStack.item
            var infoY = cardY + cardH - 20f

            // Large Item Icon + Name
            val iconSize = 40f * Gdx.graphics.density
            batch.setColor(1f, 1f, 1f, 1f)
            val icon = atlasProvider.getRegion(item.iconRegion)
            batch.draw(icon, cardX + 16f, infoY - iconSize + 10f, iconSize, iconSize)

            font.color = Color.WHITE
            font.draw(batch, item.name, cardX + iconSize + 26f, infoY)
            infoY -= 22f

            // Category & Stack count
            font.color = Color.valueOf("f1c40f")
            font.draw(batch, "${item.category.displayName}  |  Count: ${selStack.count}/${item.maxStack}", cardX + iconSize + 26f, infoY)
            infoY -= 28f

            // Description
            font.color = Color.LIGHT_GRAY
            font.draw(batch, item.description, cardX + 16f, infoY)
            infoY -= 36f

            // Extra stats: tool, consumable, placeable
            item.tool?.let {
                font.color = Color.valueOf("5dade2")
                font.draw(batch, "Tool: ${it.type.uppercase()} (Tier ${it.tier}, Power ${it.power}x)", cardX + 16f, infoY)
                infoY -= 20f
            }
            item.consumable?.let {
                font.color = Color.valueOf("2ecc71")
                val effectStr = it.effects.joinToString { eff -> "+${eff.amount.toInt()} ${eff.stat}" }
                font.draw(batch, "Restores: $effectStr", cardX + 16f, infoY)
                infoY -= 20f
            }
            item.placeable?.let {
                font.color = Color.valueOf("e67e22")
                font.draw(batch, "Placeable structure: ${it.structureId}", cardX + 16f, infoY)
                infoY -= 20f
            }

            // Action Buttons
            // USE
            batch.setColor(0.18f, 0.70f, 0.40f, 0.90f)
            batch.draw(whitePixel, btnUseX, btnUseY, btnUseW, btnUseH)
            font.color = Color.WHITE
            font.draw(batch, "USE / EQUIP", btnUseX + 10f, btnUseY + btnUseH - 10f)

            // SPLIT
            batch.setColor(0.25f, 0.45f, 0.65f, 0.90f)
            batch.draw(whitePixel, btnSplitX, btnSplitY, btnSplitW, btnSplitH)
            font.color = Color.WHITE
            font.draw(batch, "SPLIT 1/2", btnSplitX + 14f, btnSplitY + btnSplitH - 10f)

            // DROP 1
            batch.setColor(0.65f, 0.40f, 0.20f, 0.90f)
            batch.draw(whitePixel, btnDropOneX, btnDropOneY, btnDropOneW, btnDropOneH)
            font.color = Color.WHITE
            font.draw(batch, "DROP 1", btnDropOneX + 18f, btnDropOneY + btnDropOneH - 10f)

            // DROP ALL
            batch.setColor(0.70f, 0.25f, 0.25f, 0.90f)
            batch.draw(whitePixel, btnDropAllX, btnDropAllY, btnDropAllW, btnDropAllH)
            font.color = Color.WHITE
            font.draw(batch, "DROP ALL", btnDropAllX + 12f, btnDropAllY + btnDropAllH - 10f)
        } else {
            font.color = Color.GRAY
            font.draw(batch, "Select an item to view details.", cardX + 20f, cardY + cardH / 2f)
        }

        // 6. Draw floating ghost icon if actively dragging
        if (isDragging && dragFromSlot != null) {
            val dragStack = inventory.get(dragFromSlot!!)
            if (dragStack != null) {
                val icon = atlasProvider.getRegion(dragStack.item.iconRegion)
                val ghostSize = slotSize * 1.15f
                batch.setColor(1f, 1f, 1f, 0.85f)
                batch.draw(icon, currentDragX - ghostSize / 2f, currentDragY - ghostSize / 2f, ghostSize, ghostSize)
            }
        }

        batch.setColor(1f, 1f, 1f, 1f)
    }
}
