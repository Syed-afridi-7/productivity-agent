package com.syedafridi.productivity_agent.bus

import java.util.concurrent.CopyOnWriteArrayList

object NativeAgentBus {
    private val listeners = CopyOnWriteArrayList<(Map<String, Any>) -> Unit>()

    fun addListener(listener: (Map<String, Any>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Map<String, Any>) -> Unit) {
        listeners.remove(listener)
    }

    fun clearListeners() {
        listeners.clear()
    }

    fun emit(event: Map<String, Any>) {
        try {
            android.util.Log.d("ProductivityAgent", "Event emitted: $event")
        } catch (_: Throwable) {
            println("[ProductivityAgent] Event emitted: $event")
        }
        for (listener in listeners) {
            try {
                listener(event)
            } catch (_: Throwable) {}
        }
    }
}
