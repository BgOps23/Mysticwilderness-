package com.realdeelstudios.mysticwilderness.data

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

interface IAtlasProvider : Disposable {
    fun getRegion(name: String): TextureRegion
    fun hasRegion(name: String): Boolean
}

class PlaceholderAtlasProvider : IAtlasProvider {
    private val textures = HashMap<String, Texture>()
    private val regions = HashMap<String, TextureRegion>()

    init {
        createTileTextures()
        createPlayerTextures()
        createUiTextures()
        createItemTextures()
    }

    override fun getRegion(name: String): TextureRegion {
        return regions[name] ?: regions["white_pixel"]
            ?: throw IllegalStateException("Region not found: $name and fallback missing")
    }

    override fun hasRegion(name: String): Boolean = regions.containsKey(name)

    private fun register(name: String, pixmap: Pixmap) {
        val texture = Texture(pixmap).apply {
            setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
        }
        pixmap.dispose()
        textures[name] = texture
        regions[name] = TextureRegion(texture)
    }

    private fun createTileTextures() {
        // 1. Grass Meadow
        val grassPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("557a46"))
            fill()
            setColor(Color.valueOf("699052"))
            drawPixel(3, 4); drawPixel(4, 3); drawPixel(11, 10); drawPixel(12, 9)
            drawPixel(7, 12); drawPixel(8, 11); drawPixel(14, 2)
            setColor(Color.valueOf("446338"))
            drawPixel(5, 5); drawPixel(13, 11); drawPixel(9, 13)
        }
        register("tile_grass", grassPixmap)

        // 2. Dirt Path
        val dirtPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("8b5a2b"))
            fill()
            setColor(Color.valueOf("a06d3b"))
            drawPixel(2, 6); drawPixel(3, 6); drawPixel(9, 2); drawPixel(13, 12)
            setColor(Color.valueOf("6b4226"))
            drawPixel(5, 10); drawPixel(10, 8); drawPixel(14, 4)
        }
        register("tile_dirt", dirtPixmap)

        // 3. Stone Floor
        val stonePixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("7f8c8d"))
            fill()
            setColor(Color.valueOf("95a5a6"))
            drawRectangle(1, 1, 6, 6)
            drawRectangle(8, 1, 7, 6)
            drawRectangle(1, 8, 7, 7)
            drawRectangle(9, 8, 6, 7)
            setColor(Color.valueOf("566573"))
            drawLine(0, 7, 15, 7)
            drawLine(7, 0, 7, 7)
            drawLine(8, 8, 8, 15)
        }
        register("tile_stone_floor", stonePixmap)

        // 4. Dark Cave Ground
        val caveGroundPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("2c3e50"))
            fill()
            setColor(Color.valueOf("34495e"))
            drawPixel(4, 5); drawPixel(10, 11); drawPixel(13, 3)
            setColor(Color.valueOf("1a252f"))
            drawPixel(2, 12); drawPixel(8, 4); drawPixel(12, 14)
        }
        register("tile_cave_ground", caveGroundPixmap)

        // 5. Water
        val waterPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("2980b9"))
            fill()
            setColor(Color.valueOf("5dade2"))
            drawLine(2, 4, 6, 4)
            drawLine(9, 10, 13, 10)
            setColor(Color.valueOf("1f618d"))
            drawLine(3, 5, 5, 5)
            drawLine(10, 11, 12, 11)
        }
        register("tile_water", waterPixmap)

        // 6. Tree Trunk (Solid)
        val treeTrunkPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("557a46"))
            fill()
            // Bark base
            setColor(Color.valueOf("4a2e18"))
            fillRectangle(5, 2, 6, 14)
            setColor(Color.valueOf("6e4524"))
            fillRectangle(6, 4, 3, 12)
            setColor(Color.valueOf("2f1b0e"))
            drawRectangle(5, 2, 6, 14)
        }
        register("tile_tree_trunk", treeTrunkPixmap)

        // 7. Tree Canopy (Sorts over player)
        val treeTopPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color.valueOf("22542a"))
            fillCircle(8, 8, 7)
            setColor(Color.valueOf("2d6e37"))
            fillCircle(8, 7, 5)
            setColor(Color.valueOf("3e8e4c"))
            fillCircle(7, 5, 3)
        }
        register("tile_tree_top", treeTopPixmap)

        // 8. Rock Boulder (Solid)
        val rockPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("557a46"))
            fill()
            setColor(Color.valueOf("34495e"))
            fillCircle(8, 8, 6)
            setColor(Color.valueOf("7f8c8d"))
            fillCircle(7, 7, 4)
            setColor(Color.valueOf("bdc3c7"))
            fillCircle(6, 6, 2)
        }
        register("tile_rock", rockPixmap)

        // 9. Cave Wall (Solid)
        val caveWallPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("17202a"))
            fill()
            setColor(Color.valueOf("273746"))
            fillRectangle(1, 1, 14, 14)
            setColor(Color.valueOf("566573"))
            drawPixel(3, 4); drawPixel(11, 5); drawPixel(7, 10)
        }
        register("tile_cave_wall", caveWallPixmap)

        // 10. Border Wall (Solid Perimeter)
        val borderPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("1b2631"))
            fill()
            setColor(Color.valueOf("2e4053"))
            drawRectangle(1, 1, 14, 14)
            drawLine(1, 8, 14, 8)
            drawLine(8, 1, 8, 8)
            drawLine(5, 8, 5, 14)
            drawLine(12, 8, 12, 14)
            setColor(Color.valueOf("1b4f72"))
            drawPixel(4, 4); drawPixel(11, 11)
        }
        register("tile_border_wall", borderPixmap)

        // 11. Wood Floor
        val woodFloorPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("935116"))
            fill()
            setColor(Color.valueOf("af601a"))
            fillRectangle(0, 1, 16, 3)
            fillRectangle(0, 5, 16, 3)
            fillRectangle(0, 9, 16, 3)
            fillRectangle(0, 13, 16, 3)
            setColor(Color.valueOf("6e2c00"))
            drawLine(0, 0, 15, 0)
            drawLine(0, 4, 15, 4)
            drawLine(0, 8, 15, 8)
            drawLine(0, 12, 15, 12)
        }
        register("tile_wood_floor", woodFloorPixmap)

        // 12. Cabin Wall (Solid)
        val cabinWallPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("5b2c08"))
            fill()
            setColor(Color.valueOf("783f12"))
            fillRectangle(1, 1, 14, 6)
            fillRectangle(1, 8, 14, 6)
            setColor(Color.valueOf("3e1b04"))
            drawRectangle(1, 1, 14, 6)
            drawRectangle(1, 8, 14, 6)
        }
        register("tile_cabin_wall", cabinWallPixmap)

        // 13. Roof Thatch
        val roofPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("b7950b"))
            fill()
            setColor(Color.valueOf("d4ac0d"))
            for (y in 1..14 step 3) {
                drawLine(0, y, 15, y)
            }
            setColor(Color.valueOf("7d6608"))
            for (y in 3..15 step 3) {
                drawLine(0, y, 15, y)
            }
        }
        register("tile_roof", roofPixmap)
    }

    private fun createPlayerTextures() {
        val directions = listOf("down", "up", "left", "right")

        for (dir in directions) {
            val p = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
                setColor(Color.valueOf("00000000"))
                fill()

                // Boots / feet (bottom row 1-3)
                setColor(Color.valueOf("2c3e50"))
                fillRectangle(4, 13, 3, 2)
                fillRectangle(9, 13, 3, 2)

                // Legs / pants (row 11-12)
                setColor(Color.valueOf("34495e"))
                fillRectangle(4, 11, 8, 2)

                // Tunic / body (row 6-10)
                setColor(Color.valueOf("c0392b"))
                fillRectangle(3, 6, 10, 5)
                setColor(Color.valueOf("e74c3c"))
                fillRectangle(4, 7, 8, 4)

                // Head / face (row 2-5)
                setColor(Color.valueOf("f5cba7"))
                fillRectangle(4, 2, 8, 4)

                // Hair / Hood
                setColor(Color.valueOf("6e2c00"))
                fillRectangle(4, 1, 8, 2)

                // Direction details
                when (dir) {
                    "down" -> {
                        // Eyes facing forward
                        setColor(Color.valueOf("1a252f"))
                        drawPixel(6, 4)
                        drawPixel(9, 4)
                        // Belt buckle
                        setColor(Color.valueOf("f1c40f"))
                        drawPixel(7, 10); drawPixel(8, 10)
                    }
                    "up" -> {
                        // Back of hood
                        setColor(Color.valueOf("6e2c00"))
                        fillRectangle(4, 2, 8, 4)
                        // Cape detail
                        setColor(Color.valueOf("922b21"))
                        fillRectangle(5, 7, 6, 4)
                    }
                    "left" -> {
                        // Profile left
                        setColor(Color.valueOf("1a252f"))
                        drawPixel(5, 4)
                        // Left-pointing hand
                        setColor(Color.valueOf("f5cba7"))
                        drawPixel(2, 8); drawPixel(2, 9)
                    }
                    "right" -> {
                        // Profile right
                        setColor(Color.valueOf("1a252f"))
                        drawPixel(10, 4)
                        // Right-pointing hand
                        setColor(Color.valueOf("f5cba7"))
                        drawPixel(13, 8); drawPixel(13, 9)
                    }
                }
            }
            register("player_$dir", p)
        }
    }

    private fun createUiTextures() {
        // 1x1 white pixel for debug shapes / borders
        val whitePixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
            setColor(Color.WHITE)
            fill()
        }
        register("white_pixel", whitePixmap)

        // Joystick base (128x128 for crisp scaling)
        val joyBasePixmap = Pixmap(128, 128, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.1f, 0.15f, 0.2f, 0.45f))
            fillCircle(64, 64, 60)
            setColor(Color(0.9f, 0.95f, 1.0f, 0.65f))
            drawCircle(64, 64, 60)
            drawCircle(64, 64, 59)
            // Center crosshair marker
            setColor(Color(1f, 1f, 1f, 0.25f))
            drawLine(58, 64, 70, 64)
            drawLine(64, 58, 64, 70)
        }
        register("ui_joystick_base", joyBasePixmap)

        // Joystick knob (64x64)
        val joyKnobPixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.2f, 0.7f, 0.4f, 0.75f))
            fillCircle(32, 32, 28)
            setColor(Color(1f, 1f, 1f, 0.9f))
            drawCircle(32, 32, 28)
            setColor(Color(0.4f, 0.9f, 0.5f, 0.9f))
            fillCircle(28, 28, 8)
        }
        register("ui_joystick_knob", joyKnobPixmap)

        // Action button unpressed (96x96)
        val actionBtnPixmap = Pixmap(96, 96, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.15f, 0.45f, 0.25f, 0.7f))
            fillCircle(48, 48, 44)
            setColor(Color(0.4f, 0.9f, 0.5f, 0.95f))
            drawCircle(48, 48, 44)
            drawCircle(48, 48, 43)
            // Action icon: Sword / Spark glyph
            setColor(Color.WHITE)
            drawLine(48, 28, 48, 68)
            drawLine(36, 44, 60, 44)
            drawLine(40, 52, 56, 52)
        }
        register("ui_action_button", actionBtnPixmap)

        // Action button pressed
        val actionBtnPressedPixmap = Pixmap(96, 96, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.3f, 0.85f, 0.45f, 0.9f))
            fillCircle(48, 48, 44)
            setColor(Color(1f, 1f, 1f, 1.0f))
            drawCircle(48, 48, 44)
            drawCircle(48, 48, 43)
            setColor(Color.valueOf("142c16"))
            drawLine(48, 28, 48, 68)
            drawLine(36, 44, 60, 44)
            drawLine(40, 52, 56, 52)
        }
        register("ui_action_button_pressed", actionBtnPressedPixmap)

        // Backpack icon button (64x64)
        val backpackPixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Leather pack body
            setColor(Color.valueOf("783f04"))
            fillRectangle(12, 16, 40, 40)
            setColor(Color.valueOf("a64d79"))
            fillRectangle(14, 18, 36, 16)
            // Flap and straps
            setColor(Color.valueOf("4e2602"))
            drawRectangle(12, 16, 40, 40)
            fillRectangle(20, 16, 6, 40)
            fillRectangle(38, 16, 6, 40)
            // Gold buckles
            setColor(Color.valueOf("f1c40f"))
            fillRectangle(20, 32, 6, 4)
            fillRectangle(38, 32, 6, 4)
            // Top handle
            setColor(Color.valueOf("4e2602"))
            drawRectangle(24, 8, 16, 8)
        }
        register("ui_backpack_icon", backpackPixmap)

        // UI Slot Background (64x64)
        val slotBgPixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.12f, 0.16f, 0.20f, 0.85f))
            fillRectangle(0, 0, 64, 64)
            setColor(Color(0.25f, 0.32f, 0.40f, 0.95f))
            drawRectangle(0, 0, 64, 64)
            drawRectangle(1, 1, 62, 62)
            // Inner corner accents
            setColor(Color(0.40f, 0.50f, 0.60f, 0.50f))
            drawPixel(2, 2); drawPixel(61, 2); drawPixel(2, 61); drawPixel(61, 61)
        }
        register("ui_slot_bg", slotBgPixmap)

        // UI Slot Selected Highlight (64x64)
        val slotSelPixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color(0.18f, 0.80f, 0.44f, 0.35f))
            fillRectangle(0, 0, 64, 64)
            setColor(Color(0.18f, 0.95f, 0.50f, 1.0f))
            drawRectangle(0, 0, 64, 64)
            drawRectangle(1, 1, 62, 62)
            drawRectangle(2, 2, 60, 60)
        }
        register("ui_slot_selected", slotSelPixmap)
    }

    private fun createItemTextures() {
        // 1. Wood log
        val woodPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color.valueOf("6e4524"))
            fillRectangle(2, 4, 12, 8)
            setColor(Color.valueOf("4a2e18"))
            drawRectangle(2, 4, 12, 8)
            // Annual rings
            setColor(Color.valueOf("d2b48c"))
            fillRectangle(2, 5, 2, 6)
            setColor(Color.valueOf("6e4524"))
            drawPixel(3, 8)
        }
        register("item_wood", woodPixmap)

        // 2. Stone
        val stonePixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color.valueOf("7f8c8d"))
            fillCircle(8, 8, 6)
            setColor(Color.valueOf("95a5a6"))
            fillCircle(7, 7, 4)
            setColor(Color.valueOf("34495e"))
            drawPixel(5, 5); drawPixel(11, 9); drawPixel(9, 12)
        }
        register("item_stone", stonePixmap)

        // 3. Plant Fiber
        val fiberPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color.valueOf("27ae60"))
            drawLine(3, 13, 13, 3)
            drawLine(4, 14, 14, 4)
            setColor(Color.valueOf("2ecc71"))
            drawLine(4, 12, 12, 4)
            drawLine(6, 14, 14, 6)
            setColor(Color.valueOf("1e8449"))
            drawPixel(8, 8); drawPixel(6, 10); drawPixel(10, 6)
        }
        register("item_plant_fiber", fiberPixmap)

        // 4. Wild Berries
        val berriesPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Green stem & leaf
            setColor(Color.valueOf("27ae60"))
            drawLine(8, 2, 8, 6)
            drawPixel(9, 3); drawPixel(10, 2)
            // Red / purple berries
            setColor(Color.valueOf("c0392b"))
            fillCircle(6, 8, 3)
            fillCircle(10, 8, 3)
            fillCircle(8, 12, 3)
            setColor(Color.valueOf("e74c3c"))
            drawPixel(5, 7); drawPixel(9, 7); drawPixel(7, 11)
        }
        register("item_berries", berriesPixmap)

        // 5. Rabbit Meat
        val meatPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Bone cut
            setColor(Color.WHITE)
            fillCircle(5, 8, 3)
            setColor(Color.valueOf("bdc3c7"))
            drawPixel(5, 8)
            // Meat shank
            setColor(Color.valueOf("b03a2e"))
            fillCircle(10, 8, 5)
            setColor(Color.valueOf("cb4335"))
            fillCircle(9, 7, 3)
        }
        register("item_rabbit_meat", meatPixmap)

        // 6. Stone Axe
        val axePixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Handle
            setColor(Color.valueOf("873600"))
            drawLine(3, 13, 11, 5)
            drawLine(4, 14, 12, 6)
            // Stone blade
            setColor(Color.valueOf("7f8c8d"))
            fillRectangle(9, 2, 5, 5)
            setColor(Color.valueOf("bdc3c7"))
            drawLine(9, 2, 14, 2)
            drawLine(14, 2, 14, 7)
            setColor(Color.valueOf("2c3e50"))
            drawPixel(10, 5)
        }
        register("item_stone_axe", axePixmap)

        // 7. Stone Pickaxe
        val pickaxePixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Handle
            setColor(Color.valueOf("873600"))
            drawLine(3, 13, 11, 5)
            // Pick head (curved stone)
            setColor(Color.valueOf("7f8c8d"))
            drawLine(6, 2, 14, 10)
            drawLine(7, 1, 15, 9)
            setColor(Color.valueOf("bdc3c7"))
            drawPixel(6, 2); drawPixel(15, 9)
        }
        register("item_stone_pickaxe", pickaxePixmap)

        // 8. Campfire
        val campfirePixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Stone ring
            setColor(Color.valueOf("7f8c8d"))
            drawCircle(8, 9, 6)
            // Wood logs
            setColor(Color.valueOf("6e2c00"))
            drawLine(4, 11, 12, 7)
            drawLine(4, 7, 12, 11)
            // Flame
            setColor(Color.valueOf("e67e22"))
            fillCircle(8, 7, 3)
            setColor(Color.valueOf("f1c40f"))
            fillCircle(8, 6, 2)
        }
        register("item_campfire", campfirePixmap)

        // 9. Wooden Wall
        val wallPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            setColor(Color.valueOf("6e4524"))
            fillRectangle(1, 2, 14, 12)
            setColor(Color.valueOf("4a2e18"))
            drawRectangle(1, 2, 14, 12)
            drawLine(1, 6, 14, 6)
            drawLine(1, 10, 14, 10)
            drawLine(8, 2, 8, 6)
            drawLine(5, 6, 5, 10)
            drawLine(11, 6, 11, 10)
            drawLine(8, 10, 8, 14)
        }
        register("item_wooden_wall", wallPixmap)

        // 10. Bed
        val bedPixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888).apply {
            setColor(Color.valueOf("00000000"))
            fill()
            // Frame
            setColor(Color.valueOf("5b2c08"))
            fillRectangle(2, 3, 12, 10)
            // Pillow
            setColor(Color.WHITE)
            fillRectangle(3, 4, 10, 3)
            // Blanket
            setColor(Color.valueOf("2980b9"))
            fillRectangle(3, 7, 10, 5)
            setColor(Color.valueOf("3498db"))
            drawLine(3, 7, 12, 7)
        }
        register("item_bed", bedPixmap)
    }

    override fun dispose() {
        textures.values.forEach { it.dispose() }
        textures.clear()
        regions.clear()
    }
}
