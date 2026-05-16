package com.proxipad.gesture

sealed class GestureEvent {
    data class Move(var dx: Int, var dy: Int) : GestureEvent()
    object Tap : GestureEvent()
    object RightTap : GestureEvent()
    data class Scroll(var amount: Int) : GestureEvent()
}
