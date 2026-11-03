package com.realdeelstudios.mysticwilderness.data

import com.badlogic.gdx.Gdx
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TileDef(
    val id: String,
    val name: String,
    val textureRegion: String,
    val solid: Boolean,
    val walkable: Boolean
)

object TileRegistry {
    const val EMPTY_TILE_ID: Short = -1

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private var defs: Array<TileDef> = emptyArray()
    private val idToNumeric = HashMap<String, Short>()
    private val solidFlags = ArrayList<Boolean>()

    fun load(jsonContent: String) {
        val list = jsonParser.decodeFromString<List<TileDef>>(jsonContent)
        defs = list.toTypedArray()
        idToNumeric.clear()
        solidFlags.clear()

        list.forEachIndexed { index, tileDef ->
            val shortId = index.toShort()
            idToNumeric[tileDef.id] = shortId
            solidFlags.add(tileDef.solid)
        }
        Gdx.app?.log("TileRegistry", "Loaded ${defs.size} tile definitions from JSON.")
    }

    fun getId(nameId: String): Short = idToNumeric[nameId] ?: EMPTY_TILE_ID

    fun getDef(numericId: Short): TileDef? {
        val idx = numericId.toInt()
        return if (idx in defs.indices) defs[idx] else null
    }

    fun isSolid(numericId: Short): Boolean {
        val idx = numericId.toInt()
        return if (idx in solidFlags.indices) solidFlags[idx] else false
    }

    fun getCount(): Int = defs.size
}
