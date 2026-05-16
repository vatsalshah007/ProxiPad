package com.proxipad.ui

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import com.proxipad.bluetooth.HidProfileManager
import com.proxipad.gesture.GestureEngine

class MainActivity : Activity() {
    private lateinit var hidProfileManager: HidProfileManager
    
    // Phase 4b: temporary instance for testing
    private val gestureEngine = GestureEngine { event ->
        // In a later phase, this will be passed to HidReportSender
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        hidProfileManager = HidProfileManager(this)
        hidProfileManager.init()
    }

    override fun onDestroy() {
        super.onDestroy()
        hidProfileManager.release()
    }

    // Phase 4b: Temporarily feed touch events from the Activity to test the engine
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureEngine.process(event)
        return super.onTouchEvent(event)
    }
}
