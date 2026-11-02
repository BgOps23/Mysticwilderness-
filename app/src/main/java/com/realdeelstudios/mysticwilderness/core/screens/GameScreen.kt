package com.realdeelstudios.mysticwilderness.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.Direction
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemMetadata
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemTag
import com.realdeelstudios.mysticwilderness.ecs.components.FacingComponent
import com.realdeelstudios.mysticwilderness.ecs.components.InventoryComponent
import com.realdeelstudios.mysticwilderness.ecs.components.ItemStackComponent
import com.realdeelstudios.mysticwilderness.ecs.components.PickupDelay
import com.realdeelstudios.mysticwilderness.ecs.components.PlayerTag
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.SpriteComponent
import com.realdeelstudios.mysticwilderness.ecs.components.Velocity
import com.realdeelstudios.mysticwilderness.ecs.systems.CollisionSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.FacingSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.InputSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.ItemPickupSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.MovementSystem
import com.realdeelstudios.mysticwilderness.input.TouchInputProcessor
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import com.realdeelstudios.mysticwilderness.inventory.ItemStack
import com.realdeelstudios.mysticwilderness.ui.HotbarRenderer
import com.realdeelstudios.mysticwilderness.ui.HudRenderer
import com.realdeelstudios.mysticwilderness.ui.InventoryModal
import com.realdeelstudios.mysticwilderness.world.GameMap
import com.realdeelstudios.mysticwilderness.world.WorldRenderer
import ktx.app.KtxScreen
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

class GameScreen(
    private val batch: SpriteBatch,
    private val atlasProvider: IAtlasProvider,
    private val gameMap: GameMap
) : KtxScreen {

    private val camera = OrthographicCamera()
    private val uiCamera = OrthographicCamera()
    private val worldRenderer = WorldRenderer(gameMap, atlasProvider, batch)
    private val hudRenderer = HudRenderer(atlasProvider, batch)
    private val hotbarRenderer = HotbarRenderer(atlasProvider, batch, hudRenderer.font)
    private val inventoryModal: InventoryModal
    private val inputProcessor = TouchInputProcessor()
    private val multiplexer = InputMultiplexer()

    private val fleksWorld: World
    private val playerEntity: Entity
    private val playerInventory: InventoryLogic

    private var accumulator: Float = 0f
    private var viewportWidth: Float = GameConfig.TARGET_VIRTUAL_WIDTH
    private var viewportHeight: Float = GameConfig.TARGET_VIRTUAL_WIDTH * 0.5625f
    private var integerScale: Int = 1

    private var cameraX: Float = 0f
    private var cameraY: Float = 0f
    private var nextDropOrder: Long = 0L

    init {
        // 1. Configure Fleks ECS world
        fleksWorld = configureWorld {
            systems {
                add(InputSystem(inputProcessor.state, World.family { all(PlayerTag, Velocity) }))
                add(MovementSystem(World.family { all(Position, Velocity) }))
                add(CollisionSystem(gameMap, World.family { all(Position, Velocity, Collider) }))
                add(FacingSystem(World.family { all(Velocity, FacingComponent) }))
                add(
                    ItemPickupSystem(
                        family = World.family { all(DroppedItemTag, Position, ItemStackComponent, PickupDelay, DroppedItemMetadata) },
                        playerFamily = World.family { all(PlayerTag, Position, InventoryComponent) },
                        onInventoryFull = { hudRenderer.triggerInventoryFullAlert() }
                    )
                )
            }
        }

        // 2. Spawn player entity
        val spawnX = 50f * GameConfig.TILE_SIZE
        val spawnY = 46f * GameConfig.TILE_SIZE

        playerInventory = InventoryLogic(size = 20, hotbarSize = 5)

        playerEntity = fleksWorld.entity {
            it += PlayerTag()
            it += Position(spawnX, spawnY, spawnX, spawnY)
            it += Velocity(0f, 0f)
            it += Collider(
                width = GameConfig.PLAYER_COLLIDER_WIDTH,
                height = GameConfig.PLAYER_COLLIDER_HEIGHT,
                offsetX = GameConfig.PLAYER_COLLIDER_OFFSET_X,
                offsetY = GameConfig.PLAYER_COLLIDER_OFFSET_Y
            )
            it += FacingComponent()
            it += SpriteComponent(regionPrefix = "player")
            it += InventoryComponent(playerInventory)
        }

        cameraX = spawnX
        cameraY = spawnY

        // 3. Initialize InventoryModal
        inventoryModal = InventoryModal(
            atlasProvider = atlasProvider,
            batch = batch,
            font = hudRenderer.font,
            onDropItem = { stack -> spawnDroppedItem(stack) }
        ).apply {
            onClose = {
                accumulator = 0f
            }
        }

        // 4. Configure touch input routing
        inputProcessor.onBackpackClicked = {
            inventoryModal.toggle()
            inputProcessor.isModalOpen = inventoryModal.isOpen
            if (!inventoryModal.isOpen) {
                accumulator = 0f
            }
        }

        inputProcessor.onGiveStarterItemsClicked = {
            giveStarterItems()
        }

        setupInputMultiplexer()
    }

    private fun setupInputMultiplexer() {
        // Modal input adapter: receives touches first
        val modalAdapter = object : InputAdapter() {
            private fun toUiY(screenY: Int): Float = Gdx.graphics.height.toFloat() - screenY.toFloat()

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (!inventoryModal.isOpen) return false
                val handled = inventoryModal.handleTouchDown(screenX.toFloat(), toUiY(screenY), playerInventory)
                inputProcessor.isModalOpen = inventoryModal.isOpen
                return handled
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (!inventoryModal.isOpen) return false
                return inventoryModal.handleTouchDragged(screenX.toFloat(), toUiY(screenY), playerInventory)
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (!inventoryModal.isOpen) return false
                val handled = inventoryModal.handleTouchUp(screenX.toFloat(), toUiY(screenY), playerInventory)
                inputProcessor.isModalOpen = inventoryModal.isOpen
                return handled
            }
        }

        // Hotbar input adapter: checks hotbar touches
        val hotbarAdapter = object : InputAdapter() {
            private fun toUiY(screenY: Int): Float = Gdx.graphics.height.toFloat() - screenY.toFloat()

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (inventoryModal.isOpen) return false
                return hotbarRenderer.handleTouchDown(screenX.toFloat(), toUiY(screenY), playerInventory)
            }
        }

        multiplexer.addProcessor(modalAdapter)
        multiplexer.addProcessor(hotbarAdapter)
        multiplexer.addProcessor(inputProcessor)
        Gdx.input.inputProcessor = multiplexer
    }

    private fun spawnDroppedItem(stack: ItemStack) {
        val (pPos, pFacing) = with(fleksWorld) {
            val pos = playerEntity[Position]
            val facing = playerEntity[FacingComponent]
            pos to facing
        }

        val stepDist = 18f
        var dropX = pPos.x
        var dropY = pPos.y

        when (pFacing.direction) {
            Direction.UP -> dropY += stepDist
            Direction.DOWN -> dropY -= stepDist
            Direction.LEFT -> dropX -= stepDist
            Direction.RIGHT -> dropX += stepDist
        }

        // Clamp to map boundary and check tile solidity
        val tileX = floor((dropX + 8f) / GameConfig.TILE_SIZE).toInt()
        val tileY = floor((dropY + 8f) / GameConfig.TILE_SIZE).toInt()
        if (gameMap.isTileSolid(tileX, tileY)) {
            // Drop directly at player's feet if target tile is solid
            dropX = pPos.x
            dropY = pPos.y
        }

        fleksWorld.entity {
            it += DroppedItemTag()
            it += Position(dropX, dropY, dropX, dropY)
            it += ItemStackComponent(stack)
            it += PickupDelay(remainingSeconds = 1.0f)
            it += DroppedItemMetadata(creationOrder = ++nextDropOrder)
        }

        Gdx.app.log("Inventory", "Dropped: ${stack.count}x ${stack.item.name} at ($dropX, $dropY)")
    }

    private fun giveStarterItems() {
        ItemRegistry.get("stone_axe")?.let { playerInventory.add(it, 1) }
        ItemRegistry.get("stone_pickaxe")?.let { playerInventory.add(it, 1) }
        // Overflow beyond maxStack (99 + 51 = 150)
        ItemRegistry.get("wood")?.let { playerInventory.add(it, 150) }
        // Overflow beyond maxStack (99 + 21 = 120)
        ItemRegistry.get("stone")?.let { playerInventory.add(it, 120) }
        ItemRegistry.get("plant_fiber")?.let { playerInventory.add(it, 45) }
        ItemRegistry.get("berries")?.let { playerInventory.add(it, 25) }
        ItemRegistry.get("rabbit_meat")?.let { playerInventory.add(it, 8) }
        ItemRegistry.get("campfire")?.let { playerInventory.add(it, 2) }
        ItemRegistry.get("wooden_wall")?.let { playerInventory.add(it, 15) }
        ItemRegistry.get("bed")?.let { playerInventory.add(it, 1) }

        Gdx.app.log("Debug", "Gave starter items to player inventory.")
    }

    override fun show() {
        accumulator = 0f
        Gdx.input.inputProcessor = multiplexer
    }

    override fun resume() {
        accumulator = 0f
    }

    override fun resize(width: Int, height: Int) {
        integerScale = max(1, (width.toFloat() / GameConfig.TARGET_VIRTUAL_WIDTH).roundToInt())
        viewportWidth = width.toFloat() / integerScale
        viewportHeight = height.toFloat() / integerScale

        camera.setToOrtho(false, viewportWidth, viewportHeight)
        uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())

        val density = Gdx.graphics.density
        inputProcessor.updateScreenDimensions(width.toFloat(), height.toFloat(), density)
        hudRenderer.updateScreenDimensions(width.toFloat(), height.toFloat(), density)
        hotbarRenderer.updateLayout(width.toFloat(), density)
        inventoryModal.updateLayout(width.toFloat(), height.toFloat(), density)
    }

    override fun render(delta: Float) {
        val frameTime = min(delta, GameConfig.MAX_FRAME_DELTA)

        // Simulation pauses while inventory modal is open
        if (!inventoryModal.isOpen) {
            accumulator += frameTime
            while (accumulator >= GameConfig.FIXED_DELTA_TIME) {
                fleksWorld.update(GameConfig.FIXED_DELTA_TIME)
                accumulator -= GameConfig.FIXED_DELTA_TIME
            }
        }

        val alpha = (accumulator / GameConfig.FIXED_DELTA_TIME).coerceIn(0f, 1f)

        // Interpolate player position
        val (interpX, interpY, playerFootRow, directionName) = with(fleksWorld) {
            val pPos = playerEntity[Position]
            val pFacing = playerEntity[FacingComponent]
            val pCollider = playerEntity[Collider]

            val iX = pPos.prevX + (pPos.x - pPos.prevX) * alpha
            val iY = pPos.prevY + (pPos.y - pPos.prevY) * alpha
            val footY = iY + pCollider.offsetY
            val footRow = floor(footY / GameConfig.TILE_SIZE).toInt()
            Quad(iX, iY, footRow, pFacing.direction.name.lowercase())
        }

        // Camera follow
        val targetCamX = interpX + 8f
        val targetCamY = interpY + 8f
        val smoothFactor = 1f - exp(-GameConfig.CAMERA_SMOOTH_DECAY * frameTime)
        cameraX += (targetCamX - cameraX) * smoothFactor
        cameraY += (targetCamY - cameraY) * smoothFactor

        val halfW = viewportWidth / 2f
        val halfH = viewportHeight / 2f
        cameraX = cameraX.coerceIn(halfW, gameMap.widthInPx - halfW)
        cameraY = cameraY.coerceIn(halfH, gameMap.heightInPx - halfH)

        camera.position.set(round(cameraX).toFloat(), round(cameraY).toFloat(), 0f)
        camera.update()

        // Clear screen
        Gdx.gl.glClearColor(0.08f, 0.12f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        worldRenderer.updateFrustumCulling(camera, viewportWidth, viewportHeight)

        // === WORLD RENDERING ===
        batch.projectionMatrix = camera.combined
        batch.begin()

        // Pass 1: Ground and floor
        worldRenderer.renderGroundAndFloor()

        // Pass 2: Walls / Objects and Y-sorted Entities
        // Collect dropped items and render row-by-row
        val droppedItemsFamily = World.family { all(DroppedItemTag, Position, ItemStackComponent, DroppedItemMetadata) }

        for (ty in worldRenderer.maxTileY downTo worldRenderer.minTileY) {
            worldRenderer.renderWallsAndObjectsForRow(ty)

            // Render dropped items belonging to row ty
            with(fleksWorld) {
                droppedItemsFamily.forEach { itemEntity ->
                    val iPos = itemEntity[Position]
                    val iRow = floor(iPos.y / GameConfig.TILE_SIZE).toInt()
                    if (iRow == ty) {
                        val stackComp = itemEntity[ItemStackComponent]
                        val meta = itemEntity[DroppedItemMetadata]
                        val bob = sin(meta.bobTimer * 4.5f) * 1.8f
                        val icon = atlasProvider.getRegion(stackComp.stack.item.iconRegion)

                        val drawX = round(iPos.x).toFloat() + 2f
                        val drawY = round(iPos.y + bob).toFloat() + 2f
                        batch.draw(icon, drawX, drawY, 12f, 12f)
                    }
                }
            }

            // Render player entity when row matches foot baseline
            if (ty == playerFootRow) {
                val regionName = "player_$directionName"
                val playerRegion = atlasProvider.getRegion(regionName)
                val drawX = round(interpX).toFloat()
                val drawY = round(interpY).toFloat()
                batch.draw(playerRegion, drawX, drawY, 16f, 16f)
            }
        }

        // Pass 3: Roofs
        worldRenderer.renderRoofs()

        batch.end()

        // === HUD & MODAL RENDERING ===
        uiCamera.update()
        batch.projectionMatrix = uiCamera.combined
        batch.begin()

        val playerTileX = floor(interpX / GameConfig.TILE_SIZE).toInt()
        val playerTileY = floor(interpY / GameConfig.TILE_SIZE).toInt()

        // Draw hotbar (bottom-center)
        hotbarRenderer.render(playerInventory)

        // Draw HUD controls & debug overlay
        hudRenderer.render(
            inputState = inputProcessor.state,
            isDebugEnabled = inputProcessor.isDebugOverlayEnabled,
            playerTileX = playerTileX,
            playerTileY = playerTileY,
            chunksRendered = worldRenderer.chunksRenderedCount,
            totalChunks = gameMap.totalChunksCount,
            entityCount = fleksWorld.numEntities,
            delta = frameTime
        )

        // Draw Inventory Modal (if open)
        inventoryModal.render(playerInventory)

        batch.end()
    }

    override fun dispose() {
        fleksWorld.dispose()
        hudRenderer.dispose()
    }

    private data class Quad(val interpX: Float, val interpY: Float, val footRow: Int, val direction: String)
}
