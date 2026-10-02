package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.bus.NativeAgentBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeAgentBusTest {

    @Before
    fun setUp() {
        NativeAgentBus.clearListeners()
    }

    @After
    fun tearDown() {
        NativeAgentBus.clearListeners()
    }

    @Test
    fun testSubscribeAndEmit() {
        val receivedEvents = mutableListOf<Map<String, Any>>()
        val listener: (Map<String, Any>) -> Unit = { event ->
            receivedEvents.add(event)
        }

        NativeAgentBus.addListener(listener)

        val testEvent = mapOf("type" to "TEST_EVENT", "payload" to 123)
        NativeAgentBus.emit(testEvent)

        assertEquals(1, receivedEvents.size)
        assertEquals(testEvent, receivedEvents[0])
    }

    @Test
    fun testUnsubscribe() {
        val receivedEvents = mutableListOf<Map<String, Any>>()
        val listener: (Map<String, Any>) -> Unit = { event ->
            receivedEvents.add(event)
        }

        NativeAgentBus.addListener(listener)
        NativeAgentBus.removeListener(listener)

        val testEvent = mapOf("type" to "TEST_EVENT", "payload" to "hello")
        NativeAgentBus.emit(testEvent)

        assertTrue(receivedEvents.isEmpty())
    }

    @Test
    fun testClearListeners() {
        val receivedEvents1 = mutableListOf<Map<String, Any>>()
        val receivedEvents2 = mutableListOf<Map<String, Any>>()
        val listener1: (Map<String, Any>) -> Unit = { event -> receivedEvents1.add(event) }
        val listener2: (Map<String, Any>) -> Unit = { event -> receivedEvents2.add(event) }

        NativeAgentBus.addListener(listener1)
        NativeAgentBus.addListener(listener2)

        NativeAgentBus.clearListeners()

        val testEvent = mapOf("type" to "CLEAR_TEST")
        NativeAgentBus.emit(testEvent)

        assertTrue(receivedEvents1.isEmpty())
        assertTrue(receivedEvents2.isEmpty())
    }
}
