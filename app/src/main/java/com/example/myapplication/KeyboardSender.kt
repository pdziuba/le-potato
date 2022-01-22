package com.example.myapplication

import android.util.Log
import android.view.KeyEvent
import android.view.View

class KeyboardSender: View.OnKeyListener {
    val tag = "KeyboardSender"

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(tag, "Received event ${event.toString()} with keycode $keyCode")
        return false
    }
}