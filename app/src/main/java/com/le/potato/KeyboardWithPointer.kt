package com.le.potato

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.le.potato.transport.HIDTransport

class KeyboardWithPointer(private val context: Context) {
    private var _hidTransport: HIDTransport? = null

    var hidTransport: HIDTransport?
        get() = _hidTransport
        set(value) {
            _hidTransport = value
            value?.init(context, reportMap)
        }

    private fun addInputReport(reportId: Int, report: ByteArray) {
        Log.d("addInputReport", report.joinToString { "%02X".format(it) })
        hidTransport?.addInputReport(reportId, report.clone())
    }

    fun sendKeyDown(isCtrl: Boolean, isShift: Boolean, isAlt: Boolean, keyCode: Int) {
        if (!eventKeycodeToReportMap.containsKey(keyCode)) {
            Log.d(TAG, "Unknown keycode $keyCode")
            return
        }
        val report = ByteArray(8)
        setModifierField(isCtrl, isShift, isAlt, report)
        if (report[KEY_PACKET_MODIFIER_KEY_INDEX] != MODIFIER_KEY_NONE.toByte()) {
            addInputReport(1, report)
        }
        report[KEY_PACKET_KEY_INDEX] = eventKeycodeToReportMap[keyCode]!!.toByte()
        addInputReport(1, report)
    }

    private fun setModifierField(
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        report: ByteArray
    ) {
        var modifier = MODIFIER_KEY_NONE
        if (isCtrl) modifier = modifier or MODIFIER_KEY_CTRL
        if (isShift) modifier = modifier or MODIFIER_KEY_SHIFT
        if (isAlt) modifier = modifier or MODIFIER_KEY_ALT
        report[KEY_PACKET_MODIFIER_KEY_INDEX] = modifier.toByte()
    }

    fun sendKeyUp(isCtrl: Boolean, isShift: Boolean, isAlt: Boolean, keyCode: Int) {
        if (!eventKeycodeToReportMap.containsKey(keyCode)) {
            Log.d(TAG, "Unknown keycode $keyCode")
            return
        }
        val report = ByteArray(8)
        setModifierField(isCtrl, isShift, isAlt, report)
        addInputReport(1, report)
        if (report[KEY_PACKET_MODIFIER_KEY_INDEX] != MODIFIER_KEY_NONE.toByte()) {
            addInputReport(1, EMPTY_KEYBOARD_REPORT)
        }
    }

    private val lastSent = ByteArray(4)

    private fun clamp(input: Int): Int {
        return kotlin.math.max(-127, kotlin.math.min(127, input))
    }

    fun movePointer(dx: Int, dy: Int, wheel: Int, leftButton: Boolean, rightButton: Boolean, middleButton: Boolean) {
        val dx = clamp(dx)
        val dy = clamp(dy)
        val wheel = clamp(wheel)
        var button = 0
        if (leftButton) {
            button = button or 1
        }
        if (rightButton) {
            button = button or 2
        }
        if (middleButton) {
            button = button or 4
        }
        val report = ByteArray(5)
        report[0] = button.toByte()
        report[1] = dx.toByte()
        report[2] = dy.toByte()
        report[3] = wheel.toByte()

        var hasNonZero = false
        for (v in lastSent + report) {
            if (v != (0).toByte()){
                hasNonZero = true
                break
            }
        }
        if (!hasNonZero) return

        lastSent[0] = report[0]
        lastSent[1] = report[1]
        lastSent[2] = report[2]
        lastSent[3] = report[3]
        addInputReport(2, report)
    }

    companion object {
        private val TAG = KeyboardWithPointer::class.java.simpleName
        const val MODIFIER_KEY_NONE = 0
        const val MODIFIER_KEY_CTRL = 1
        const val MODIFIER_KEY_SHIFT = 2
        const val MODIFIER_KEY_ALT = 4

        val eventKeycodeToReportMap = mapOf(
            KeyEvent.KEYCODE_A to 0x04,
            KeyEvent.KEYCODE_B to 0x05,
            KeyEvent.KEYCODE_C to 0x06,
            KeyEvent.KEYCODE_D to 0x07,
            KeyEvent.KEYCODE_E to 0x08,
            KeyEvent.KEYCODE_F to 0x09,
            KeyEvent.KEYCODE_G to 0x0A,
            KeyEvent.KEYCODE_H to 0x0B,
            KeyEvent.KEYCODE_I to 0x0C,
            KeyEvent.KEYCODE_J to 0x0D,
            KeyEvent.KEYCODE_K to 0x0E,
            KeyEvent.KEYCODE_L to 0x0F,
            KeyEvent.KEYCODE_M to 0x10,
            KeyEvent.KEYCODE_N to 0x11,
            KeyEvent.KEYCODE_O to 0x12,
            KeyEvent.KEYCODE_P to 0x13,
            KeyEvent.KEYCODE_Q to 0x14,
            KeyEvent.KEYCODE_R to 0x15,
            KeyEvent.KEYCODE_S to 0x16,
            KeyEvent.KEYCODE_T to 0x17,
            KeyEvent.KEYCODE_U to 0x18,
            KeyEvent.KEYCODE_V to 0x19,
            KeyEvent.KEYCODE_W to 0x1A,
            KeyEvent.KEYCODE_X to 0x1B,
            KeyEvent.KEYCODE_Y to 0x1C,
            KeyEvent.KEYCODE_Z to 0x1D,
            KeyEvent.KEYCODE_1 to 0x1E,
            KeyEvent.KEYCODE_2 to 0x1F,
            KeyEvent.KEYCODE_3 to 0x20,
            KeyEvent.KEYCODE_4 to 0x21,
            KeyEvent.KEYCODE_5 to 0x22,
            KeyEvent.KEYCODE_6 to 0x23,
            KeyEvent.KEYCODE_7 to 0x24,
            KeyEvent.KEYCODE_8 to 0x25,
            KeyEvent.KEYCODE_9 to 0x26,
            KeyEvent.KEYCODE_0 to 0x27,
            KeyEvent.KEYCODE_ENTER to 0x28,
            KeyEvent.KEYCODE_ESCAPE to 0x29,
            KeyEvent.KEYCODE_DEL to 0x2A,
            KeyEvent.KEYCODE_TAB to 0x2B,
            KeyEvent.KEYCODE_SPACE to 0x2C,
            KeyEvent.KEYCODE_MINUS to 0x2D,
            KeyEvent.KEYCODE_EQUALS to 0x2E,
            KeyEvent.KEYCODE_LEFT_BRACKET to 0x2F,
            KeyEvent.KEYCODE_RIGHT_BRACKET to 0x30,
            KeyEvent.KEYCODE_BACKSLASH to 0x31,
            KeyEvent.KEYCODE_POUND to 0x32,
            KeyEvent.KEYCODE_SEMICOLON to 0x33,
            KeyEvent.KEYCODE_APOSTROPHE to 0x34,
            KeyEvent.KEYCODE_GRAVE to 0x35,
            KeyEvent.KEYCODE_COMMA to 0x36,
            KeyEvent.KEYCODE_PERIOD to 0x37,
            KeyEvent.KEYCODE_SLASH to 0x38,
            KeyEvent.KEYCODE_CAPS_LOCK to 0x39,
            KeyEvent.KEYCODE_F1 to 0x3A,
            KeyEvent.KEYCODE_F2 to 0x3B,
            KeyEvent.KEYCODE_F3 to 0x3C,
            KeyEvent.KEYCODE_F4 to 0x3D,
            KeyEvent.KEYCODE_F5 to 0x3E,
            KeyEvent.KEYCODE_F6 to 0x3F,
            KeyEvent.KEYCODE_F7 to 0x40,
            KeyEvent.KEYCODE_F8 to 0x41,
            KeyEvent.KEYCODE_F9 to 0x42,
            KeyEvent.KEYCODE_F10 to 0x43,
            KeyEvent.KEYCODE_F11 to 0x44,
            KeyEvent.KEYCODE_F12 to 0x45,
            KeyEvent.KEYCODE_SCROLL_LOCK to 0x47,
            KeyEvent.KEYCODE_INSERT to 0x49,
            KeyEvent.KEYCODE_HOME to 0x4A,
            KeyEvent.KEYCODE_PAGE_UP to 0x4B,
            KeyEvent.KEYCODE_FORWARD_DEL to 0x4C,
            KeyEvent.KEYCODE_MOVE_END to 0x4D,
            KeyEvent.KEYCODE_PAGE_DOWN to 0x4E,
            KeyEvent.KEYCODE_DPAD_RIGHT to 0x4F,
            KeyEvent.KEYCODE_DPAD_LEFT to 0x50,
            KeyEvent.KEYCODE_DPAD_DOWN to 0x51,
            KeyEvent.KEYCODE_DPAD_UP to 0x52,
        )

        /**
         * Characteristic Data(Report Map)
         */
        private val keyboardReportMap = byteArrayOf(
            USAGE_PAGE(1), 0x01,  // Generic Desktop Ctrls
            USAGE(1), 0x06,  // Keyboard
            COLLECTION(1), 0x01,  // Application
            REPORT_ID(1), 0x01,
            USAGE_PAGE(1), 0x07,  //   Kbrd/Keypad
            USAGE_MINIMUM(1), 0xE0.toByte(),
            USAGE_MAXIMUM(1), 0xE7.toByte(),
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x01,
            REPORT_SIZE(1), 0x01,  //   1 byte (Modifier)
            REPORT_COUNT(1), 0x08,
            INPUT(1), 0x02,  //   Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position
            REPORT_COUNT(1), 0x01,  //   1 byte (Reserved)
            REPORT_SIZE(1), 0x08,
            INPUT(1), 0x01,  //   Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position
            REPORT_COUNT(1), 0x05,  //   5 bits (Num lock, Caps lock, Scroll lock, Compose, Kana)
            REPORT_SIZE(1), 0x01,
            USAGE_PAGE(1), 0x08,  //   LEDs
            USAGE_MINIMUM(1), 0x01,  //   Num Lock
            USAGE_MAXIMUM(1), 0x05,  //   Kana
            OUTPUT(1), 0x02,  //   Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile
            REPORT_COUNT(1), 0x01,  //   3 bits (Padding)
            REPORT_SIZE(1), 0x03,
            OUTPUT(1), 0x01,  //   Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile
            REPORT_COUNT(1), 0x06,  //   6 bytes (Keys)
            REPORT_SIZE(1), 0x08,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x65,  //   101 keys
            USAGE_PAGE(1), 0x07,  //   Kbrd/Keypad
            USAGE_MINIMUM(1), 0x00,
            USAGE_MAXIMUM(1), 0x65,
            INPUT(1), 0x00,  //   Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position
            END_COLLECTION(0)
        )
        private val mouseReportMap = byteArrayOf(
            USAGE_PAGE(1), 0x01,  // Generic Desktop
            USAGE(1), 0x02,  // Mouse
            COLLECTION(1), 0x01,  // Application
            REPORT_ID(1), 0x02,
            USAGE(1), 0x01,  //  Pointer
            COLLECTION(1), 0x00,  //  Physical
            USAGE_PAGE(1), 0x09,  //   Buttons
            USAGE_MINIMUM(1), 0x01,
            USAGE_MAXIMUM(1), 0x03,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x01,
            REPORT_COUNT(1), 0x03,  //   3 bits (Buttons)
            REPORT_SIZE(1), 0x01,
            INPUT(1), 0x02,  //   Data, Variable, Absolute
            REPORT_COUNT(1), 0x01,  //   5 bits (Padding)
            REPORT_SIZE(1), 0x05,
            INPUT(1), 0x01,  //   Constant
            USAGE_PAGE(1), 0x01,  //   Generic Desktop
            USAGE(1), 0x30,  //   X
            USAGE(1), 0x31,  //   Y
            USAGE(1), 0x38,  //   Wheel
            LOGICAL_MINIMUM(1), 0x81.toByte(),  //   -127
            LOGICAL_MAXIMUM(1), 0x7f,  //   127
            REPORT_SIZE(1), 0x08,  //   Three bytes
            REPORT_COUNT(1), 0x03,
            INPUT(1), 0x06,  //   Data, Variable, Relative
            END_COLLECTION(0),
            END_COLLECTION(0)
        )
        val reportMap = keyboardReportMap + mouseReportMap
        private const val KEY_PACKET_MODIFIER_KEY_INDEX = 0
        private const val KEY_PACKET_KEY_INDEX = 2
        private val EMPTY_KEYBOARD_REPORT = ByteArray(8)
    }
}