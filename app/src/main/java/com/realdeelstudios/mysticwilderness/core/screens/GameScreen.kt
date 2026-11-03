package com.realdeelstudios.mysticwilderness.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Family
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import com.realdeelstudios.mysticwilderness.core.GameClock
import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import com.realdeelstudios.mysticwilderness.data.NodeDef
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.ecs.components.Collider
import com.realdeelstudios.mysticwilderness.ecs.components.Direction
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemMetadata
import com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemTag
import com.realdeelstudios.mysticwilderness.ecs.components.FacingComponent
import com.realdeelstudios.mysticwilderness.ecs.components.InventoryComponent
import com.realdeelstudios.mysticwilderness.ecs.components.ItemStackComponent
import com.realdeelstudios.mysticwilderness.ecs.components.NodeSaveData
import com.realdeelstudios.mysticwilderness.ecs.components.NodeState
import com.realdeelstudios.mysticwilderness.ecs.components.PickupDelay
import com.realdeelstudios.mysticwilderness.ecs.components.PlayerTag
import com.realdeelstudios.mysticwilderness.ecs.components.Position
import com.realdeelstudios.mysticwilderness.ecs.components.ResourceNodeComponent
import com.realdeelstudios.mysticwilderness.ecs.components.SpriteComponent
import com.realdeelstudios.mysticwilderness.ecs.components.Velocity
import com.realdeelstudios.mysticwilderness.ecs.systems.CollisionSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.FacingSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.GatherSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.InputSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.ItemPickupSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.MovementSystem
import com.realdeelstudios.mysticwilderness.ecs.systems.RespawnSystem
import com.realdeelstudios.mysticwilderness.input.TouchInputProcessor
import com.realdeelstudios.mysticwilderness.inventory.InventoryLogic
import com.realdeelstudios.mysticwilderness.inventory.ItemStack
import com.realdeelstudios.mysticwilderness.ui.HotbarRenderer
import com.realdeelstudios.mysticwilderness.ui.HudRenderer
import com.realdeelstudios.mysticwilderness.ui.InventoryModal
import com.realdeelstudios.mysticwilderness.world.GameMap
import com.realdeelstudios.mysticwilderness.world.NodeSpatialIndex
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

    val gameClock = GameClock()
    val nodeSpatialIndex = NodeSpatialIndex(gameMap.widthInTiles, gameMap.heightInTiles)

    private val fleksWorld: World
    private val droppedItemsFamily: Family
    private val resourceNodesFamily: Family
    private val gatherSystem: GatherSystem
    private val respawnSystem: RespawnSystem
    private val playerEntity: Entity
    private val playerInventory: InventoryLogic
    private var totalNodesCount: Int = 0

    private var accumulator: Float = 0f
    private var viewportWidth: Float = GameConfig.TARGET_VIRTUAL_WIDTH
    private var viewportHeight: Float = GameConfig.TARGET_VIRTUAL_WIDTH * 0.5625f
    private var integerScale: Int = 1

    private var cameraX: Float = 0f
    private var cameraY: Float = 0f
    private var nextDropOrder: Long = 0L

    init {
        gameMap.nodeSpatialIndex = nodeSpatialIndex

        var itemsFamily: Family? = null
        var nodesFam: Family? = null
        var gSystem: GatherSystem? = null
        var rSystem: RespawnSystem? = null

        // 1. Configure Fleks ECS world
        fleksWorld = configureWorld {
            itemsFamily = World.family { all(DroppedItemTag, Position, ItemStackComponent, DroppedItemMetadata) }
            nodesFam = World.family { all(ResourceNodeComponent, Position, SpriteComponent) }
            val pFamily = World.family { all(PlayerTag, Position, InventoryComponent, FacingComponent) }

            systems {
                add(InputSystem(inputProcessor.state, World.family { all(PlayerTag, Velocity) }))
                add(MovementSystem(World.family { all(Position, Velocity) }))
                add(CollisionSystem(gameMap, World.family { all(Position, Velocity, Collider) }))
                add(FacingSystem(World.family { all(Velocity, FacingComponent) }))
                val gs = GatherSystem(
                    family = nodesFam!!,
                    playerFamily = pFamily,
                    inputState = inputProcessor.state,
                    gameClock = gameClock,
                    spatialIndex = nodeSpatialIndex,
                    onTriggerHudMessage = { hudRenderer.showFloatingMessage(it) },
                    onSpawnWorldItem = { stack, x, y -> spawnDroppedItem(stack, x, y) }
                )
                gSystem = gs
                add(gs)
                add(
                    ItemPickupSystem(
                        family = itemsFamily!!,
                        playerFamily = World.family { all(PlayerTag, Position, InventoryComponent) },
                        onInventoryFull = { hudRenderer.triggerInventoryFullAlert() }
                    )
                )
                val rs = RespawnSystem(
                    family = nodesFam!!,
                    playerFamily = pFamily,
                    gameClock = gameClock,
                    spatialIndex = nodeSpatialIndex,
                    onNodeRespawned = { gSystem?.invalidateTargetCache() }
                )
                rSystem = rs
                add(rs)
            }
        }
        droppedItemsFamily = itemsFamily!!
        resourceNodesFamily = nodesFam!!
        gatherSystem = gSystem!!
        respawnSystem = rSystem!!

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

        // 3. Spawn seeded resource nodes
        val placements = gameMap.generateNodePlacements()
        totalNodesCount = placements.size
        placements.forEach { spec ->
            val nodeDef = NodeRegistry.get(spec.nodeDefId)
            if (nodeDef != null) {
                val nodeX = spec.tileX * GameConfig.TILE_SIZE
                val nodeY = spec.tileY * GameConfig.TILE_SIZE
                val entity = fleksWorld.entity {
                    it += Position(nodeX, nodeY, nodeX, nodeY)
                    it += SpriteComponent(regionPrefix = nodeDef.spriteRegion)
                    it += ResourceNodeComponent(
                        nodeDefId = nodeDef.id,
                        currentHealth = nodeDef.maxHealth,
                        maxHealth = nodeDef.maxHealth,
                        state = NodeState.HEALTHY
                    )
                }
                nodeSpatialIndex.registerNode(
                    entity = entity,
                    tileX = spec.tileX,
                    tileY = spec.tileY,
                    blocksMovement = nodeDef.blocksMovement
                )
            }
        }

        // 4. Initialize InventoryModal
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

        // 5. Configure touch input routing
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

        inputProcessor.onAdvanceDayClicked = {
            gameClock.advanceByDays(1)
            gatherSystem.invalidateTargetCache()
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

    private fun spawnDroppedItem(stack: ItemStack, customX: Float? = null, customY: Float? = null) {
        val dropX: Float
        val dropY: Float

        if (customX != null && customY != null) {
            dropX = customX
            dropY = customY
        } else {
            val (pPos, pFacing) = with(fleksWorld) {
                val pos = playerEntity[Position]
                val facing = playerEntity[FacingComponent]
                pos to facing
            }

            val stepDist = 18f
            var candX = pPos.x
            var candY = pPos.y

            when (pFacing.direction) {
                Direction.UP -> candY += stepDist
                Direction.DOWN -> candY -= stepDist
                Direction.LEFT -> candX -= stepDist
                Direction.RIGHT -> candX += stepDist
            }

            // Clamp to map boundary and check tile solidity
            val tileX = floor((candX + 8f) / GameConfig.TILE_SIZE).toInt()
            val tileY = floor((candY + 8f) / GameConfig.TILE_SIZE).toInt()
            if (gameMap.isTileSolid(tileX, tileY)) {
                // Drop directly at player's feet if target tile is solid
                dropX = pPos.x
                dropY = pPos.y
            } else {
                dropX = candX
                dropY = candY
            }
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
                gameClock.tick(GameConfig.FIXED_DELTA_TIME)
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
        var visibleNodesCount = 0
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

                // Render resource nodes belonging to row ty
                resourceNodesFamily.forEach { nodeEntity ->
                    val nodeComp = nodeEntity[ResourceNodeComponent]
                    if (nodeComp.state == NodeState.DEPLETED_HIDDEN) return@forEach

                    val nPos = nodeEntity[Position]
                    val nodeDef = NodeRegistry.get(nodeComp.nodeDefId)
                    val sortAnchor = nodeDef?.sortAnchor ?: 0f
                    val nRow = floor((nPos.y + sortAnchor) / GameConfig.TILE_SIZE).toInt()

                    if (nRow == ty) {
                        val nX = nPos.x
                        val nY = nPos.y

                        // Frustum culling check
                        if (nX + 32f >= cameraX - halfW && nX - 16f <= cameraX + halfW &&
                            nY + 48f >= cameraY - halfH && nY - 16f <= cameraY + halfH
                        ) {
                            visibleNodesCount++

                            // Hit shake
                            var shakeX = 0f
                            var shakeY = 0f
                            if (nodeComp.hitShakeTimer > 0f) {
                                shakeX = sin(nodeComp.hitShakeTimer * 60f) * 1.5f
                                shakeY = sin(nodeComp.hitShakeTimer * 40f) * 1.0f
                            }

                            val spriteName = if (nodeComp.state == NodeState.DEPLETED && nodeDef?.depletedSpriteRegion != null) {
                                nodeDef.depletedSpriteRegion!!
                            } else {
                                nodeDef?.spriteRegion ?: "white_pixel"
                            }
                            val region = atlasProvider.getRegion(spriteName)

                            val drawW = region.regionWidth.toFloat()
                            val drawH = region.regionHeight.toFloat()
                            val drawX = round(nX + shakeX).toFloat()
                            val drawY = round(nY + shakeY).toFloat()

                            // Damage flash
                            if (nodeComp.damageFlashTimer > 0f) {
                                batch.setColor(1f, 0.6f, 0.6f, 1f)
                            } else {
                                batch.setColor(1f, 1f, 1f, 1f)
                            }

                            batch.draw(region, drawX, drawY, drawW, drawH)
                            batch.setColor(1f, 1f, 1f, 1f)

                            // Target reticle
                            if (nodeEntity == gatherSystem.currentTargetEntity) {
                                val reticle = atlasProvider.getRegion("target_reticle")
                                batch.draw(reticle, drawX, drawY, 16f, 16f)
                            }

                            // Health bar while damaged
                            if (nodeComp.showHealthBarTimer > 0f && nodeComp.currentHealth < nodeComp.maxHealth && nodeComp.currentHealth > 0) {
                                val hbBg = atlasProvider.getRegion("health_bar_bg")
                                val hbFill = atlasProvider.getRegion("health_bar_fill")
                                val hbX = drawX
                                val hbY = drawY + drawH + 2f
                                batch.draw(hbBg, hbX, hbY, 16f, 3f)

                                val hpRatio = (nodeComp.currentHealth.toFloat() / nodeComp.maxHealth).coerceIn(0f, 1f)
                                val fillW = 14f * hpRatio
                                if (fillW > 0.5f) {
                                    batch.draw(hbFill, hbX + 1f, hbY + 1f, fillW, 1f)
                                }
                            }
                        }
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
            visibleNodes = visibleNodesCount,
            totalNodes = totalNodesCount,
            clockTimeFormatted = gameClock.formatClockTime(),
            delta = frameTime
        )

        // Draw Inventory Modal (if open)
        inventoryModal.render(playerInventory)

        batch.end()
    }

    data class WorldSaveData(
        val mapSeed: Long,
        val clockData: GameClock.ClockSaveData,
        val modifiedNodes: List<NodeSaveData>
    )

    fun toSaveData(mapSeed: Long = 1337L): WorldSaveData {
        val modified = ArrayList<NodeSaveData>()
        with(fleksWorld) {
            resourceNodesFamily.forEach { nodeEntity ->
                val nodeComp = nodeEntity[ResourceNodeComponent]
                val nodeDef = NodeRegistry.get(nodeComp.nodeDefId)
                val isDefault = nodeDef != null &&
                    nodeComp.currentHealth == nodeDef.maxHealth &&
                    nodeComp.state == NodeState.HEALTHY &&
                    nodeComp.respawnAtGameSeconds <= 0.0

                if (!isDefault) {
                    val pos = nodeEntity[Position]
                    modified.add(
                        NodeSaveData(
                            entityX = pos.x,
                            entityY = pos.y,
                            nodeDefId = nodeComp.nodeDefId,
                            health = nodeComp.currentHealth,
                            state = nodeComp.state,
                            respawnAtGameSeconds = nodeComp.respawnAtGameSeconds
                        )
                    )
                }
            }
        }
        return WorldSaveData(
            mapSeed = mapSeed,
            clockData = gameClock.toSaveData(),
            modifiedNodes = modified
        )
    }

    fun fromSaveData(saveData: WorldSaveData) {
        gameClock.fromSaveData(saveData.clockData)
        val savedMap = HashMap<Pair<Int, Int>, NodeSaveData>()
        saveData.modifiedNodes.forEach { saved ->
            val key = saved.entityX.toInt() to saved.entityY.toInt()
            savedMap[key] = saved
        }

        with(fleksWorld) {
            resourceNodesFamily.forEach { nodeEntity ->
                val pos = nodeEntity[Position]
                val key = pos.x.toInt() to pos.y.toInt()
                val saved = savedMap[key]
                val nodeComp = nodeEntity[ResourceNodeComponent]
                val nodeDef = NodeRegistry.get(nodeComp.nodeDefId)

                val tx = floor(pos.x / GameConfig.TILE_SIZE).toInt()
                val ty = floor(pos.y / GameConfig.TILE_SIZE).toInt()

                if (saved != null) {
                    nodeComp.currentHealth = saved.health
                    nodeComp.state = saved.state
                    nodeComp.respawnAtGameSeconds = saved.respawnAtGameSeconds

                    when (saved.state) {
                        NodeState.HEALTHY -> {
                            nodeEntity[SpriteComponent].regionPrefix = nodeDef?.spriteRegion ?: ""
                            if (nodeDef?.blocksMovement == true) {
                                nodeSpatialIndex.setNodeBlocking(nodeEntity, tx, ty, isBlocking = true)
                            }
                        }
                        NodeState.DEPLETED -> {
                            nodeEntity[SpriteComponent].regionPrefix = nodeDef?.depletedSpriteRegion ?: nodeDef?.spriteRegion ?: ""
                            nodeSpatialIndex.setNodeBlocking(nodeEntity, tx, ty, isBlocking = false)
                        }
                        NodeState.DEPLETED_HIDDEN -> {
                            nodeSpatialIndex.setNodeBlocking(nodeEntity, tx, ty, isBlocking = false)
                        }
                    }
                } else if (nodeDef != null) {
                    nodeComp.currentHealth = nodeDef.maxHealth
                    nodeComp.state = NodeState.HEALTHY
                    nodeComp.respawnAtGameSeconds = 0.0
                    nodeEntity[SpriteComponent].regionPrefix = nodeDef.spriteRegion
                    if (nodeDef.blocksMovement) {
                        nodeSpatialIndex.setNodeBlocking(nodeEntity, tx, ty, isBlocking = true)
                    }
                }
            }
        }
        gatherSystem.invalidateTargetCache()
    }

    override fun dispose() {
        fleksWorld.dispose()
        hudRenderer.dispose()
    }

    private data class Quad(val interpX: Float, val interpY: Float, val footRow: Int, val direction: String)
}
