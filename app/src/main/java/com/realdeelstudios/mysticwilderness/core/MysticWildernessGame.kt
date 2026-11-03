package com.realdeelstudios.mysticwilderness.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.realdeelstudios.mysticwilderness.core.screens.GameScreen
import com.realdeelstudios.mysticwilderness.data.IAtlasProvider
import com.realdeelstudios.mysticwilderness.data.PlaceholderAtlasProvider
import com.realdeelstudios.mysticwilderness.data.TileRegistry
import com.realdeelstudios.mysticwilderness.world.GameMap
import ktx.app.KtxGame
import ktx.app.KtxScreen

class MysticWildernessGame : KtxGame<KtxScreen>() {

    private lateinit var batch: SpriteBatch
    private lateinit var atlasProvider: IAtlasProvider
    private lateinit var gameMap: GameMap

    override fun create() {
        Gdx.app.log("Game", "Starting Mystic Wilderness...")

        val tilesJson = Gdx.files.internal("tiles.json").readString()
        TileRegistry.load(tilesJson)
        val itemsJson = Gdx.files.internal("items.json").readString()
        com.realdeelstudios.mysticwilderness.data.ItemRegistry.load(itemsJson)
        val nodesJson = Gdx.files.internal("nodes.json").readString()
        com.realdeelstudios.mysticwilderness.data.NodeRegistry.load(nodesJson)

        // 2. Initialize graphics and atlas provider
        batch = SpriteBatch()
        atlasProvider = PlaceholderAtlasProvider()

        // 3. Generate initial 96x96 placeholder world
        gameMap = GameMap(widthInTiles = 96, heightInTiles = 96, chunkSize = GameConfig.CHUNK_SIZE).apply {
            generatePlaceholderWorld()
        }

        // 4. Register and launch GameScreen
        val gameScreen = GameScreen(batch, atlasProvider, gameMap)
        addScreen(gameScreen)
        setScreen<GameScreen>()
    }

    override fun dispose() {
        super.dispose()
        batch.dispose()
        atlasProvider.dispose()
    }
}
