package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.bus.AgentFocusState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentFocusStateTest {

    @Before
    fun setUp() {
        AgentFocusState.reset()
    }

    @After
    fun tearDown() {
        AgentFocusState.reset()
    }

    @Test
    fun testDefaultStateIsFalse() {
        assertFalse(AgentFocusState.isFocusActive)
    }

    @Test
    fun testToggleFocusState() {
        AgentFocusState.isFocusActive = true
        assertTrue(AgentFocusState.isFocusActive)

        AgentFocusState.isFocusActive = false
        assertFalse(AgentFocusState.isFocusActive)

        AgentFocusState.isFocusActive = true
        AgentFocusState.reset()
        assertFalse(AgentFocusState.isFocusActive)
    }
}
