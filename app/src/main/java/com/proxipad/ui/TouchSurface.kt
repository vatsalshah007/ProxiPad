package com.proxipad.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import com.proxipad.gesture.GestureEngine
import com.proxipad.gesture.GestureEvent

@SuppressLint("ViewConstructor")
class TouchSurface(
    context: Context,
    onGesture: (GestureEvent) -> Unit
) : View(context) {

    private val gestureEngine = GestureEngine(onGesture)

    init {
        // Deep dark background to make it look like a sleek trackpad
        setBackgroundColor(Color.parseColor("#121212"))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Pass all touch events to our O(1) GestureEngine
        gestureEngine.process(event)
        
        // Return true to indicate we have consumed the touch event
        return true 
    }
}
