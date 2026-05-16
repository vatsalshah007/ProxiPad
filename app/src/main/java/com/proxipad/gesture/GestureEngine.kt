package com.proxipad.gesture

import android.util.Log
import android.view.MotionEvent

class GestureEngine(private val onGesture: (GestureEvent) -> Unit) {

    // Pre-allocated objects to avoid GC pressure in ACTION_MOVE
    private val moveEvent = GestureEvent.Move(0, 0)
    private val scrollEvent = GestureEvent.Scroll(0)
    
    private var lastX = 0f
    private var lastY = 0f

    // Tap detection state
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var maxPointersDown = 1
    private var isTapValid = true

    fun process(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                startX = event.x
                startY = event.y
                downTime = event.eventTime
                maxPointersDown = 1
                isTapValid = true
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > maxPointersDown) {
                    maxPointersDown = event.pointerCount
                }
                // Reset tracking coordinates to avoid jumps when a second finger touches
                if (event.pointerCount == 2) {
                    lastX = event.x
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTapValid && event.pointerCount == maxPointersDown) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (dx * dx + dy * dy >= 100) {
                        isTapValid = false
                    }
                }

                // Phase 4b: single finger move only
                if (event.pointerCount == 1 && maxPointersDown == 1) {
                    val currentX = event.x
                    val currentY = event.y
                    
                    val dx = (currentX - lastX).toInt()
                    val dy = (currentY - lastY).toInt()

                    if (dx != 0 || dy != 0) {
                        moveEvent.dx = dx
                        moveEvent.dy = dy
                        
                        Log.d(TAG, "Single finger move: dx=$dx, dy=$dy")
                        onGesture(moveEvent)
                        
                        lastX = currentX
                        lastY = currentY
                    }
                    return true
                }
                
                // Phase 4e: two finger scroll
                if (event.pointerCount == 2) {
                    val currentY = event.y
                    // Invert Y delta for natural scrolling (downward swipe = scroll up)
                    // You can divide dy by a constant here later to adjust sensitivity
                    val dy = (lastY - currentY).toInt() 

                    if (dy != 0) {
                        scrollEvent.amount = dy
                        
                        Log.d(TAG, "Two finger scroll: amount=$dy")
                        onGesture(scrollEvent)
                        
                        lastX = event.x
                        lastY = currentY
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val timeDelta = event.eventTime - downTime
                
                if (isTapValid && timeDelta <= 150) {
                    if (maxPointersDown == 1) {
                        Log.d(TAG, "Single finger tap detected")
                        onGesture(GestureEvent.Tap)
                    } else if (maxPointersDown == 2) {
                        Log.d(TAG, "Two finger tap detected")
                        onGesture(GestureEvent.RightTap)
                    }
                }
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "GestureEngine"
    }
}
