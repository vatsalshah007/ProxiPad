package com.proxipad.gesture

sealed class GestureEvent {
    data class Move(var dx: Int, var dy: Int) : GestureEvent()
    object Tap : GestureEvent()
    object RightTap : GestureEvent()
    data class Scroll(var amount: Int) : GestureEvent()

    // Phase 8 (v2) - Two-Finger Drag-and-Drop
    object DragHoldStart : GestureEvent()
    data class DragMove(var dx: Int, var dy: Int) : GestureEvent()
    object DragRelease : GestureEvent()
}
