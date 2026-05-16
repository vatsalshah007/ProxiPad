package com.proxipad.gesture

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GestureEngineTest {

    private lateinit var engine: GestureEngine
    private var lastEvent: GestureEvent? = null

    @Before
    fun setUp() {
        engine = GestureEngine { event ->
            lastEvent = event
        }
    }

    private fun mockEvent(action: Int, x: Float, y: Float, pointerCount: Int = 1, eventTime: Long = 0L): MotionEvent {
        val event = mock(MotionEvent::class.java)
        `when`(event.actionMasked).thenReturn(action)
        `when`(event.x).thenReturn(x)
        `when`(event.y).thenReturn(y)
        `when`(event.pointerCount).thenReturn(pointerCount)
        `when`(event.eventTime).thenReturn(eventTime)
        return event
    }

    @Test
    fun `Single finger move produces correct delta X and Y values`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f))
        engine.process(mockEvent(MotionEvent.ACTION_MOVE, 15f, 5f))

        assertTrue(lastEvent is GestureEvent.Move)
        val move = lastEvent as GestureEvent.Move
        assertEquals(5, move.dx)
        assertEquals(-5, move.dy)
    }

    @Test
    fun `Tap is detected when ACTION_UP fires within 150ms of ACTION_DOWN and total movement is under 10px`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f, eventTime = 0))
        engine.process(mockEvent(MotionEvent.ACTION_UP, 10f, 10f, eventTime = 100))

        assertTrue(lastEvent is GestureEvent.Tap)
    }

    @Test
    fun `Tap is NOT detected when total movement exceeds 10px`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f, eventTime = 0))
        engine.process(mockEvent(MotionEvent.ACTION_MOVE, 30f, 30f, eventTime = 50)) // 20px movement
        lastEvent = null // Reset last event to verify it doesn't emit Tap
        engine.process(mockEvent(MotionEvent.ACTION_UP, 30f, 30f, eventTime = 100))

        assertNull(lastEvent)
    }

    @Test
    fun `Two-finger tap produces a RightTap GestureEvent`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f, pointerCount = 1, eventTime = 0))
        engine.process(mockEvent(MotionEvent.ACTION_POINTER_DOWN, 10f, 10f, pointerCount = 2, eventTime = 10))
        engine.process(mockEvent(MotionEvent.ACTION_UP, 10f, 10f, pointerCount = 1, eventTime = 100))

        assertTrue(lastEvent is GestureEvent.RightTap)
    }

    @Test
    fun `Two-finger vertical drag produces a Scroll GestureEvent`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f, pointerCount = 1))
        engine.process(mockEvent(MotionEvent.ACTION_POINTER_DOWN, 10f, 10f, pointerCount = 2))
        
        // Simulating scrolling down on the screen (y moves from 10 to 5)
        engine.process(mockEvent(MotionEvent.ACTION_MOVE, 10f, 5f, pointerCount = 2)) 
        
        assertTrue(lastEvent is GestureEvent.Scroll)
        val scroll = lastEvent as GestureEvent.Scroll
        assertEquals(5, scroll.amount)
    }

    @Test
    fun `Verify the same GestureEvent instance is reused across multiple ACTION_MOVE events`() {
        engine.process(mockEvent(MotionEvent.ACTION_DOWN, 10f, 10f))
        
        engine.process(mockEvent(MotionEvent.ACTION_MOVE, 15f, 10f))
        val firstEvent = lastEvent

        engine.process(mockEvent(MotionEvent.ACTION_MOVE, 20f, 10f))
        val secondEvent = lastEvent

        assertSame("The engine must reuse the same pre-allocated GestureEvent.Move instance", firstEvent, secondEvent)
    }
}
