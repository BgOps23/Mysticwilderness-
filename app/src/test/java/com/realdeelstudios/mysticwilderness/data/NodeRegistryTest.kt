package com.realdeelstudios.mysticwilderness.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class NodeRegistryTest {

    @Before
    fun setup() {
        NodeRegistry.clear()
    }

    @Test
    fun load_validNodes_succeeds() {
        val json = """
        [
          {
            "id": "test_tree",
            "name": "Test Tree",
            "spriteRegion": "node_tree",
            "maxHealth": 100,
            "drops": [
              { "itemId": "wood", "minCount": 1, "maxCount": 3, "chance": 1.0 }
            ],
            "respawnGameSeconds": { "min": 100.0, "max": 200.0 }
          }
        ]
        """.trimIndent()

        NodeRegistry.load(json, itemValidator = { it == "wood" }, atlasValidator = { it == "node_tree" })
        val def = NodeRegistry.get("test_tree")
        assertNotNull(def)
    }

    @Test
    fun load_duplicateId_throwsErrorNamingNode() {
        val json = """
        [
          { "id": "dup_node", "name": "Node 1", "spriteRegion": "r", "maxHealth": 10 },
          { "id": "dup_node", "name": "Node 2", "spriteRegion": "r", "maxHealth": 10 }
        ]
        """.trimIndent()

        try {
            NodeRegistry.load(json)
            fail("Expected exception for duplicate ID")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error mentions duplicate node ID", e.message?.contains("dup_node") == true)
        }
    }

    @Test
    fun load_unknownDropItemId_throwsErrorNamingNode() {
        val json = """
        [
          {
            "id": "bad_drop_node",
            "name": "Bad Drop",
            "spriteRegion": "r",
            "maxHealth": 10,
            "drops": [
              { "itemId": "unregistered_item_xyz", "minCount": 1, "maxCount": 2, "chance": 1.0 }
            ]
          }
        ]
        """.trimIndent()

        try {
            NodeRegistry.load(json, itemValidator = { false })
            fail("Expected exception for unknown itemId")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error mentions bad_drop_node", e.message?.contains("bad_drop_node") == true)
            assertTrue("Error mentions unknown item", e.message?.contains("unregistered_item_xyz") == true)
        }
    }

    @Test
    fun load_minCountGreaterThanMaxCount_throwsErrorNamingNode() {
        val json = """
        [
          {
            "id": "inverted_counts_node",
            "name": "Inverted Counts",
            "spriteRegion": "r",
            "maxHealth": 10,
            "drops": [
              { "itemId": "wood", "minCount": 5, "maxCount": 2, "chance": 1.0 }
            ]
          }
        ]
        """.trimIndent()

        try {
            NodeRegistry.load(json, itemValidator = { true })
            fail("Expected exception for minCount > maxCount")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error mentions inverted_counts_node", e.message?.contains("inverted_counts_node") == true)
        }
    }

    @Test
    fun load_chanceOutsideZeroToOne_throwsErrorNamingNode() {
        val json = """
        [
          {
            "id": "invalid_chance_node",
            "name": "Invalid Chance",
            "spriteRegion": "r",
            "maxHealth": 10,
            "drops": [
              { "itemId": "wood", "minCount": 1, "maxCount": 2, "chance": 1.5 }
            ]
          }
        ]
        """.trimIndent()

        try {
            NodeRegistry.load(json, itemValidator = { true })
            fail("Expected exception for chance > 1.0")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error mentions invalid_chance_node", e.message?.contains("invalid_chance_node") == true)
        }
    }

    @Test
    fun load_missingSpriteRegion_throwsErrorNamingNode() {
        val json = """
        [
          {
            "id": "missing_sprite_node",
            "name": "Missing Sprite",
            "spriteRegion": "non_existent_texture_region",
            "maxHealth": 10
          }
        ]
        """.trimIndent()

        try {
            NodeRegistry.load(json, atlasValidator = { false })
            fail("Expected exception for missing sprite region")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error mentions missing_sprite_node", e.message?.contains("missing_sprite_node") == true)
            assertTrue("Error mentions missing region name", e.message?.contains("non_existent_texture_region") == true)
        }
    }
}
