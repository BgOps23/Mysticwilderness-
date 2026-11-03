package com.realdeelstudios.mysticwilderness.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.realdeelstudios.mysticwilderness.core.screens.GameScreen
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.data.ItemRegistry
import com.realdeelstudios.mysticwilderness.data.NodeRegistry
import com.realdeelstudios.mysticwilderness.data.PlaceholderAtlasProvider
import com.realdeelstudios.mysticwilderness.data.TileRegistry
import com.realdeelstudios.mysticwilderness.world.GameMap
import ktx.app.KtxGame
import ktx.app.KtxScreen

class MysticWildernessGame : KtxGame<KtxScreen>() {

    private lateinit var batch: SpriteBatch
    private lateinit var atlasProvider: IAtlasProvider
    private lateinit var gameMap: GameMap
    private var initialized = false
    private var frameCount = 0

    override fun create() {
        Gdx.app.log("Game", "Starting Mystic Wilderness...")
    }

    override fun render() {
        if (!initialized) {
            // Render first frames immediately so SurfaceView presents a valid buffer
            // to Android SurfaceSyncGroup within <30ms, avoiding 1000ms transaction timeouts.
            Gdx.gl.glClearColor(0.12f, 0.15f, 0.12f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            frameCount++
            if (frameCount >= 2) {
                initGame()
                initialized = true
            }
            return
        }
        super.render()
    }

    private fun initGame() {
        val tilesJson = Gdx.files.internal("tiles.json").readString()
        TileRegistry.load(tilesJson)
        val itemsJson = Gdx.files.internal("items.json").readString()
        ItemRegistry.load(itemsJson)
        val nodesJson = Gdx.files.internal("nodes.json").readString()
        NodeRegistry.load(nodesJson)

        batch = SpriteBatch()
        atlasProvider = PlaceholderAtlasProvider()

        gameMap = GameMap(widthInTiles = 96, heightInTiles = 96, chunkSize = GameConfig.CHUNK_SIZE).apply {
            generatePlaceholderWorld()
        }

        val gameScreen = GameScreen(batch, atlasProvider, gameMap)
        addScreen(gameScreen)
        setScreen<GameScreen>()
    }

    override fun dispose() {
        super.dispose()
        if (::batch.isInitialized) {
            batch.dispose()
        }
        if (::atlasProvider.isInitialized) {
            atlasProvider.dispose()
        }
    }
}

