package com.syedafridi.productivity_agent.bus

import java.util.concurrent.atomic.AtomicBoolean

object AgentFocusState {
    private val focusActive = AtomicBoolean(false)

    var isFocusActive: Boolean
        get() = focusActive.get()
        set(value) = focusActive.set(value)

    fun reset() {
        focusActive.set(false)
    }
}
