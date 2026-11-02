package com.realdeelstudios.mysticwilderness.data

import kotlinx.serialization.json.Json

object ItemRegistry {
    const val EMPTY_ITEM_INDEX: Int = -1

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var itemsList: Array<ItemDef> = emptyArray()
    private val idToIndexMap = HashMap<String, Int>()
    private val indexToItemMap = HashMap<Int, ItemDef>()

    fun load(jsonContent: String) {
        val parsedList = try {
            jsonParser.decodeFromString<List<ItemDef>>(jsonContent)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse items.json: ${e.message}", e)
        }

        val seenIds = HashSet<String>()
        parsedList.forEach { item ->
            if (item.id.isBlank()) {
                throw IllegalArgumentException("Item found with blank or empty 'id' (name: '${item.name}')")
            }
            if (!seenIds.add(item.id)) {
                throw IllegalArgumentException("Duplicate item ID '${item.id}' detected in items.json")
            }
            if (item.iconRegion.isBlank()) {
                throw IllegalArgumentException("Item '${item.id}' is missing required 'iconRegion'")
            }
            if (item.maxStack < 1) {
                throw IllegalArgumentException("Item '${item.id}' has invalid maxStack ${item.maxStack} (must be >= 1)")
            }
        }

        itemsList = parsedList.toTypedArray()
        idToIndexMap.clear()
        indexToItemMap.clear()

        itemsList.forEachIndexed { index, item ->
            idToIndexMap[item.id] = index
            indexToItemMap[index] = item
        }
    }

    fun get(id: String): ItemDef? {
        val index = idToIndexMap[id] ?: return null
        return indexToItemMap[index]
    }

    fun getByIndex(index: Int): ItemDef? = indexToItemMap[index]

    fun getIndex(id: String): Int = idToIndexMap[id] ?: EMPTY_ITEM_INDEX

    fun has(id: String): Boolean = idToIndexMap.containsKey(id)

    val count: Int
        get() = itemsList.size

    fun all(): List<ItemDef> = itemsList.toList()
}
