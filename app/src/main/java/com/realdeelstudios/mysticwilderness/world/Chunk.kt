package com.realdeelstudios.mysticwilderness.world

import com.realdeelstudios.mysticwilderness.core.GameConfig
import com.realdeelstudios.mysticwilderness.data.TileRegistry

class Chunk(
    val chunkX: Int,
    val chunkY: Int,
    val size: Int = GameConfig.CHUNK_SIZE
) {
    // Primitive ShortArray per layer, initial value TileRegistry.EMPTY_TILE_ID (-1)
    val layerTiles: Array<ShortArray> = Array(TileLayer.entries.size) {
        ShortArray(size * size) { TileRegistry.EMPTY_TILE_ID }
    }

    // Requested flags
    var needsRedraw: Boolean = true
    var modifiedSinceSave: Boolean = false

    val worldX: Float = chunkX * size * GameConfig.TILE_SIZE
    val worldY: Float = chunkY * size * GameConfig.TILE_SIZE
    val worldWidth: Float = size * GameConfig.TILE_SIZE
    val worldHeight: Float = size * GameConfig.TILE_SIZE

    fun getTile(layer: TileLayer, localX: Int, localY: Int): Short {
        if (localX !in 0 until size || localY !in 0 until size) {
            return TileRegistry.EMPTY_TILE_ID
        }
        return layerTiles[layer.ordinal][localY * size + localX]
    }

    fun setTile(layer: TileLayer, localX: Int, localY: Int, tileId: Short) {
        if (localX !in 0 until size || localY !in 0 until size) return
        val index = localY * size + localX
        val current = layerTiles[layer.ordinal][index]
        if (current != tileId) {
            layerTiles[layer.ordinal][index] = tileId
            needsRedraw = true
            modifiedSinceSave = true
        }
    }

    fun isSolidAt(localX: Int, localY: Int): Boolean {
        if (localX !in 0 until size || localY !in 0 until size) return true
        val index = localY * size + localX
        // Check solid across all layers
        for (layerIdx in layerTiles.indices) {
            val tileId = layerTiles[layerIdx][index]
            if (tileId != TileRegistry.EMPTY_TILE_ID && TileRegistry.isSolid(tileId)) {
                return true
            }
        }
        return false
    }
}
