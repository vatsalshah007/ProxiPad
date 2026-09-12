package com.proxipad.gesture

import android.util.Log
import android.view.MotionEvent

class GestureEngine(private val onGesture: (GestureEvent) -> Unit) {

    // Pre-allocated objects to avoid GC pressure in ACTION_MOVE
    private val moveEvent = GestureEvent.Move(0, 0)
    private val scrollEvent = GestureEvent.Scroll(0)
    private val dragMoveEvent = GestureEvent.DragMove(0, 0)
    
    private var lastX = 0f
    private var lastY = 0f

    // Tap detection state
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var maxPointersDown = 1
    private var isTapValid = true

    // Finger 2 drag tracking state (Phase 8 v2)
    private var finger2PointerId = -1
    private var finger2StartX = 0f
    private var finger2StartY = 0f
    private var finger2LastX = 0f
    private var finger2LastY = 0f
    private var isDragActive = false

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
                
                finger2PointerId = -1
                isDragActive = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > maxPointersDown) {
                    maxPointersDown = event.pointerCount
                }
                // Record finger 2 tracking state
                if (event.pointerCount >= 2 && finger2PointerId == -1) {
                    val actionIndex = event.actionIndex
                    finger2PointerId = event.getPointerId(actionIndex)
                    finger2StartX = event.getX(actionIndex)
                    finger2StartY = event.getY(actionIndex)
                    finger2LastX = finger2StartX
                    finger2LastY = finger2StartY
                    isDragActive = false
                    
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

                // Phase 8: Two finger drag-and-drop tracking
                if (event.pointerCount >= 2 && finger2PointerId != -1) {
                    val f2Index = event.findPointerIndex(finger2PointerId)
                    if (f2Index != -1) {
                        val f2X = event.getX(f2Index)
                        val f2Y = event.getY(f2Index)

                        if (!isDragActive) {
                            val dxStart = f2X - finger2StartX
                            val dyStart = f2Y - finger2StartY
                            if (dxStart * dxStart + dyStart * dyStart >= 100) {
                                isTapValid = false
                                isDragActive = true
                                Log.d(TAG, "Drag hold start detected")
                                onGesture(GestureEvent.DragHoldStart)

                                val accumDx = (f2X - finger2StartX).toInt()
                                val accumDy = (f2Y - finger2StartY).toInt()
                                if (accumDx != 0 || accumDy != 0) {
                                    dragMoveEvent.dx = accumDx
                                    dragMoveEvent.dy = accumDy
                                    Log.d(TAG, "Initial drag move: dx=$accumDx, dy=$accumDy")
                                    onGesture(dragMoveEvent)
                                }
                                finger2LastX = f2X
                                finger2LastY = f2Y
                                return true
                            }
                        } else {
                            val dx = (f2X - finger2LastX).toInt()
                            val dy = (f2Y - finger2LastY).toInt()
                            if (dx != 0 || dy != 0) {
                                dragMoveEvent.dx = dx
                                dragMoveEvent.dy = dy
                                Log.d(TAG, "Drag move: dx=$dx, dy=$dy")
                                onGesture(dragMoveEvent)
                                finger2LastX = f2X
                                finger2LastY = f2Y
                            }
                            return true
                        }
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
                if (event.pointerCount == 2 && !isDragActive) {
                    val currentY = event.y
                    // Invert Y delta for natural scrolling (downward swipe = scroll up)
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
            MotionEvent.ACTION_POINTER_UP -> {
                if (isDragActive) {
                    isDragActive = false
                    finger2PointerId = -1
                    Log.d(TAG, "Drag release detected on ACTION_POINTER_UP")
                    onGesture(GestureEvent.DragRelease)
                } else {
                    val actionIndex = event.actionIndex
                    if (event.getPointerId(actionIndex) == finger2PointerId) {
                        finger2PointerId = -1
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragActive) {
                    isDragActive = false
                    finger2PointerId = -1
                    Log.d(TAG, "Drag release detected on ACTION_UP")
                    onGesture(GestureEvent.DragRelease)
                    return true
                }

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
                finger2PointerId = -1
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "GestureEngine"
    }
}
