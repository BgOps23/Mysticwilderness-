package com.realdeelstudios.mysticwilderness.data

import kotlinx.serialization.json.Json

object NodeRegistry {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var nodesList: Array<NodeDef> = emptyArray()
    private val idToNodeMap = HashMap<String, NodeDef>()

    fun load(
        jsonContent: String,
        itemValidator: ((String) -> Boolean)? = { ItemRegistry.has(it) },
        atlasValidator: ((String) -> Boolean)? = null
    ) {
        val parsedList = try {
            jsonParser.decodeFromString<List<NodeDef>>(jsonContent)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse nodes.json: ${e.message}", e)
        }

        val seenIds = HashSet<String>()
        parsedList.forEach { node ->
            if (node.id.isBlank()) {
                throw IllegalArgumentException("Node found with blank or empty 'id' (name: '${node.name}')")
            }
            if (!seenIds.add(node.id)) {
                throw IllegalArgumentException("Duplicate node ID '${node.id}' detected in nodes.json")
            }
            if (node.spriteRegion.isBlank()) {
                throw IllegalArgumentException("Node '${node.id}' is missing required 'spriteRegion'")
            }
            atlasValidator?.let { hasRegion ->
                if (!hasRegion(node.spriteRegion)) {
                    throw IllegalArgumentException("Node '${node.id}' references missing spriteRegion '${node.spriteRegion}'")
                }
                node.depletedSpriteRegion?.let { depleted ->
                    if (!hasRegion(depleted)) {
                        throw IllegalArgumentException("Node '${node.id}' references missing depletedSpriteRegion '$depleted'")
                    }
                }
            }
            if (node.maxHealth <= 0) {
                throw IllegalArgumentException("Node '${node.id}' has invalid maxHealth ${node.maxHealth} (must be > 0)")
            }
            if (node.respawnGameSeconds.min > node.respawnGameSeconds.max) {
                throw IllegalArgumentException("Node '${node.id}' has invalid respawnGameSeconds: min (${node.respawnGameSeconds.min}) > max (${node.respawnGameSeconds.max})")
            }

            node.drops.forEach { drop ->
                itemValidator?.let { isValidItem ->
                    if (!isValidItem(drop.itemId)) {
                        throw IllegalArgumentException("Node '${node.id}' specifies unknown drop itemId '${drop.itemId}'")
                    }
                }
                if (drop.minCount > drop.maxCount) {
                    throw IllegalArgumentException("Node '${node.id}' has invalid drop counts: minCount (${drop.minCount}) > maxCount (${drop.maxCount}) for item '${drop.itemId}'")
                }
                if (drop.chance < 0.0f || drop.chance > 1.0f) {
                    throw IllegalArgumentException("Node '${node.id}' has invalid drop chance ${drop.chance} for item '${drop.itemId}' (must be between 0.0 and 1.0)")
                }
            }
        }

        nodesList = parsedList.toTypedArray()
        idToNodeMap.clear()
        nodesList.forEach { node ->
            idToNodeMap[node.id] = node
        }
    }

    fun get(id: String): NodeDef? = idToNodeMap[id]

    fun has(id: String): Boolean = idToNodeMap.containsKey(id)

    fun getAll(): List<NodeDef> = nodesList.toList()

    fun clear() {
        nodesList = emptyArray()
        idToNodeMap.clear()
    }
}
