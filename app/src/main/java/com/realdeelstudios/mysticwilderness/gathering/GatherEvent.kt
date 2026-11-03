package com.realdeelstudios.mysticwilderness.gathering

/**
 * Event emitted whenever items are gathered from a resource node.
 */
data class GatherEvent(
    val nodeId: String,
    val itemId: String,
    val count: Int,
    val toolType: String?
)

/**
 * Lightweight event bus for gathering events.
 */
object GatherEventBus {
    private val listeners = ArrayList<(GatherEvent) -> Unit>()

    fun register(listener: (GatherEvent) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregister(listener: (GatherEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(event: GatherEvent) {
        for (i in 0 until listeners.size) {
            listeners[i].invoke(event)
        }
    }

    fun clear() {
        listeners.clear()
    }
}
