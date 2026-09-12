package com.proxipad.gesture

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GestureEngineTest {

    private lateinit var engine: GestureEngine
    private var eventsList = mutableListOf<GestureEvent>()
    private var lastEvent: GestureEvent? = null

    @Before
    fun setUp() {
        eventsList.clear()
        engine = GestureEngine { event ->
            lastEvent = event
            eventsList.add(event)
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

    private fun mockMultiPointerEvent(
        action: Int,
        actionIndex: Int = 0,
        pointerCount: Int = 2,
        x0: Float = 10f, y0: Float = 10f,
        x1: Float = 50f, y1: Float = 50f,
        eventTime: Long = 0L
    ): MotionEvent {
        val event = mock(MotionEvent::class.java)
        `when`(event.actionMasked).thenReturn(action)
        `when`(event.actionIndex).thenReturn(actionIndex)
        `when`(event.pointerCount).thenReturn(pointerCount)
        `when`(event.eventTime).thenReturn(eventTime)
        `when`(event.x).thenReturn(x0)
        `when`(event.y).thenReturn(y0)
        `when`(event.getPointerId(0)).thenReturn(0)
        `when`(event.getPointerId(1)).thenReturn(1)
        `when`(event.findPointerIndex(0)).thenReturn(0)
        `when`(event.findPointerIndex(1)).thenReturn(1)
        `when`(event.getX(0)).thenReturn(x0)
        `when`(event.getY(0)).thenReturn(y0)
        `when`(event.getX(1)).thenReturn(x1)
        `when`(event.getY(1)).thenReturn(y1)
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

    // --- Phase 8 (v2) Unit Tests ---

    @Test
    fun `Two-finger tap with no movement - no drag events, right click fires`() {
        eventsList.clear()
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointerCount = 1, eventTime = 0))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointerCount = 2, eventTime = 10))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_UP, actionIndex = 0, pointerCount = 1, eventTime = 100))

        assertFalse("No drag events should be emitted for stationary two-finger tap", eventsList.any { it is GestureEvent.DragHoldStart || it is GestureEvent.DragMove || it is GestureEvent.DragRelease })
        assertTrue("RightTap should be emitted", eventsList.any { it is GestureEvent.RightTap })
    }

    @Test
    fun `Two-finger touch where finger 2 moves past 10px slop - DragHoldStart then DragMove`() {
        eventsList.clear()
        // Finger 1 down at (10, 10)
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointerCount = 1))
        // Finger 2 down at (50, 50)
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 50f, y1 = 50f))
        
        // Finger 2 moves to (70, 50) -> 20px delta X (exceeding 10px slop threshold)
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_MOVE, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 70f, y1 = 50f))

        assertTrue("DragHoldStart must fire when finger 2 moves past slop threshold", eventsList.contains(GestureEvent.DragHoldStart))
        val dragMoveEvents = eventsList.filterIsInstance<GestureEvent.DragMove>()
        assertTrue("DragMove must be emitted carrying the initial displacement", dragMoveEvents.isNotEmpty())
        assertEquals(20, dragMoveEvents.first().dx)
        assertEquals(0, dragMoveEvents.first().dy)
    }

    @Test
    fun `Lifting finger 1 mid-drag fires DragRelease`() {
        eventsList.clear()
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointerCount = 1))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 50f, y1 = 50f))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_MOVE, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 70f, y1 = 50f))
        
        eventsList.clear()
        // Finger 1 lifted (ACTION_POINTER_UP)
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_UP, actionIndex = 0, pointerCount = 1))

        assertTrue("DragRelease must be emitted when finger 1 is lifted during drag", eventsList.contains(GestureEvent.DragRelease))
    }

    @Test
    fun `Lifting finger 2 mid-drag fires DragRelease`() {
        eventsList.clear()
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointerCount = 1))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 50f, y1 = 50f))
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_MOVE, actionIndex = 1, pointerCount = 2, x0 = 10f, y0 = 10f, x1 = 70f, y1 = 50f))
        
        eventsList.clear()
        // Finger 2 lifted (ACTION_POINTER_UP)
        engine.process(mockMultiPointerEvent(MotionEvent.ACTION_POINTER_UP, actionIndex = 1, pointerCount = 1))

        assertTrue("DragRelease must be emitted when finger 2 is lifted during drag", eventsList.contains(GestureEvent.DragRelease))
    }
}
