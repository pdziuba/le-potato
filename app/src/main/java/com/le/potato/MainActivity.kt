package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.le.potato.transport.BluetoothFacadeService
import com.le.potato.transport.DeviceConnectedListener
import com.le.potato.utils.BluetoothEnabler
import com.le.potato.utils.PermissionsHelper
import com.le.potato.utils.PermissionsResolvedListener
import kotlin.math.max


class MainActivity : AppCompatActivity(), View.OnKeyListener, DeviceConnectedListener, PermissionsResolvedListener {
    private val tag = MainActivity::class.java.simpleName
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothService: BluetoothFacadeService? = null
    private val keyboardWithPointer: KeyboardWithPointer = KeyboardWithPointer()
    private val permissionsHelper: PermissionsHelper = PermissionsHelper(this, this)
    private var currentInputFragment: Fragment? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
            val binder = _binder as BluetoothFacadeService.LocalBinder
            val service = binder.getService()

            bluetoothService = service
            if (service.connectedDevice != null) {
                onDeviceConnected(service.connectedDevice!!)
            } else {
                disableInputs()
            }
            service.registerDeviceConnectedListener(this@MainActivity)
            service.init(this@MainActivity, KeyboardWithPointer.reportMap)
            keyboardWithPointer.hidTransport = service
            setStatusText()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            keyboardWithPointer.hidTransport = null
        }
    }

    private fun setStatusText() {
        val statusTextView = findViewById<TextView>(R.id.status_text)
        StatusMixin.setStatusText(statusTextView, bluetoothService, bluetoothAdapter, this)
    }

    val touchListener = object : View.OnTouchListener {
        var maxPointerCount: Int = 0
        var X: Float = 0.0F
        var Y: Float = 0.0F
        var firstX: Float = 0.0F
        var firstY: Float = 0.0F

        override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
            val mouse = keyboardWithPointer
            if (motionEvent == null || bluetoothService?.devices?.isEmpty() == true) {
                return false
            }
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    maxPointerCount = motionEvent.pointerCount
                    X = motionEvent.x
                    Y = motionEvent.y
                    firstX = X
                    firstY = Y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    maxPointerCount = max(maxPointerCount, motionEvent.pointerCount)
                    val wheel = (0.25 * (motionEvent.y - Y)).toInt()
                    if (maxPointerCount >= 2) {
                        mouse.movePointer(
                            0,
                            0,
                            wheel,
                            leftButton = false,
                            rightButton = false,
                            middleButton = false
                        )
                    } else {
                        mouse.movePointer(
                            (motionEvent.x - X).toInt(),
                            (motionEvent.y - Y).toInt(),
                            0,
                            leftButton = false,
                            rightButton = false,
                            middleButton = false
                        )
                    }
                    X = motionEvent.x
                    Y = motionEvent.y
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    view?.performClick()
                    X = motionEvent.x
                    Y = motionEvent.y
                    if ((X - firstX) * (X - firstX) + (Y - firstY) * (Y - firstY) < 20) {
                        val leftButton = maxPointerCount == 1
                        val rightButton = maxPointerCount >= 2

                        mouse.movePointer(
                            (motionEvent.x - X).toInt(),
                            (motionEvent.y - Y).toInt(),
                            0,
                            leftButton = leftButton,
                            rightButton = rightButton,
                            middleButton = false
                        )
                        mouse.movePointer(
                            (motionEvent.x - X).toInt(),
                            (motionEvent.y - Y).toInt(),
                            0,
                            leftButton = false,
                            rightButton = false,
                            middleButton = false
                        )

                    }
                    return true
                }
            }
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        val keyboardButton = findViewById<Button>(R.id.keyboard_button)
        keyboardButton.setOnKeyListener(this)
        keyboardButton.setOnFocusChangeListener { v, hasFocus ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (hasFocus) {
                displayInputFragment(InputFragment.KEYBOARD)
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
            } else {
                imm.hideSoftInputFromWindow(v.applicationWindowToken, 0)
            }
        }
        findViewById<Button>(R.id.mouse_button).setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                displayInputFragment(InputFragment.MOUSE)
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(btReceiver, intentFilter)
    }

    override fun onStart() {
        super.onStart()
        permissionsHelper.askForPermissions()
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter?.isEnabled == false) {
            BluetoothEnabler.enableBluetooth(this)
        }
    }

    override fun onStop() {
        super.onStop()
        val bluetoothService = bluetoothService ?: return
        bluetoothService.unregisterDeviceConnectedListener(this)
        unbindService(serviceConnection)
    }

    private enum class InputFragment {
        KEYBOARD,
        MOUSE
    }

    private fun displayInputFragment(targetFragment: InputFragment?) {
        val fragment = when (targetFragment) {
            InputFragment.KEYBOARD -> KeyboardFragment()
            InputFragment.MOUSE -> MouseFragment()
            else -> null
        }
        val tx = supportFragmentManager.beginTransaction()
        tx.setReorderingAllowed(true)
        if (fragment != null) {
            if (currentInputFragment == null) {
                tx.add(R.id.fragmentContainerView, fragment)
            } else {
                tx.replace(R.id.fragmentContainerView, fragment)
            }
            currentInputFragment = fragment
        } else if (currentInputFragment != null) {
            tx.remove(currentInputFragment!!)
            currentInputFragment = null
        }
        tx.commit()
        supportFragmentManager.executePendingTransactions()
        findViewById<FragmentContainerView>(R.id.fragmentContainerView).visibility = View.VISIBLE
    }

    fun onKeyboardButtonClicked(view: View) {
        displayInputFragment(InputFragment.KEYBOARD)

        if (view.hasFocus()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        } else if (!view.requestFocus()) {
            Log.e(tag, "Cannot acquire focus")
        }
    }

    fun onMouseButtonClicked(view: View) {
        displayInputFragment(InputFragment.KEYBOARD)
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
    }

    fun showBluetoothStatus(view: View) {
        startActivity(Intent(view.context, BluetoothStatusActivity::class.java))
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        when (event?.action) {
            KeyEvent.ACTION_DOWN -> {
                Log.d(
                    tag,
                    "ACTION_DOWN ctrl = ${event.isCtrlPressed} shift = ${event.isShiftPressed} alt = ${event.isAltPressed} code = $keyCode"
                )
                keyboardWithPointer.sendKeyDown(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, keyCode)
            }
            KeyEvent.ACTION_UP -> {
                Log.d(
                    tag,
                    "ACTION_UP ctrl = ${event.isCtrlPressed} shift = ${event.isShiftPressed} alt = ${event.isAltPressed} code = $keyCode"
                )
                keyboardWithPointer.sendKeyUp(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, keyCode)
            }
            else -> {
                // todo: handle ogonki
                Log.d(tag, "Unhandled action ${event?.action}")
                return false
            }
        }
        return true
    }


    override fun onDeviceConnected(device: BluetoothDevice) {
        runOnUiThread {
            findViewById<Button>(R.id.keyboard_button).isEnabled = true
            findViewById<Button>(R.id.mouse_button).isEnabled = true
            setStatusText()
        }
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        runOnUiThread {
            setStatusText()
        }
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        disableInputs()
    }

    private fun disableInputs() {
        runOnUiThread {
            setStatusText()
            findViewById<Button>(R.id.keyboard_button).isEnabled = false
            findViewById<Button>(R.id.mouse_button).isEnabled = false
            findViewById<FragmentContainerView>(R.id.fragmentContainerView).visibility = View.INVISIBLE
        }
    }

    override fun onDeviceConnectionError(device: BluetoothDevice, error: String?) {
        runOnUiThread {
            if (error == null) {
                Toast.makeText(this, getString(R.string.connection_error), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.connection_error_details, error), Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onButtonPressed(view: View) {
        val keyCode = when (view.id) {
            R.id.key_esc -> KeyEvent.KEYCODE_ESCAPE
            R.id.key_f1 -> KeyEvent.KEYCODE_F1
            R.id.key_f2 -> KeyEvent.KEYCODE_F2
            R.id.key_f3 -> KeyEvent.KEYCODE_F3
            R.id.key_f4 -> KeyEvent.KEYCODE_F4
            R.id.key_f5 -> KeyEvent.KEYCODE_F5
            R.id.key_f6 -> KeyEvent.KEYCODE_F6
            R.id.key_f7 -> KeyEvent.KEYCODE_F7
            R.id.key_f8 -> KeyEvent.KEYCODE_F8
            R.id.key_f9 -> KeyEvent.KEYCODE_F9
            R.id.key_f10 -> KeyEvent.KEYCODE_F10
            R.id.key_f11 -> KeyEvent.KEYCODE_F11
            R.id.key_f12 -> KeyEvent.KEYCODE_F12
            R.id.key_insert -> KeyEvent.KEYCODE_INSERT
            R.id.key_delete -> KeyEvent.KEYCODE_FORWARD_DEL
            R.id.key_home -> KeyEvent.KEYCODE_HOME
            R.id.key_end -> KeyEvent.KEYCODE_MOVE_END
            R.id.key_page_up -> KeyEvent.KEYCODE_PAGE_UP
            R.id.key_page_down -> KeyEvent.KEYCODE_PAGE_DOWN
            R.id.key_left_arrow -> KeyEvent.KEYCODE_DPAD_LEFT
            R.id.key_down_arrow -> KeyEvent.KEYCODE_DPAD_DOWN
            R.id.key_right_arrow -> KeyEvent.KEYCODE_DPAD_RIGHT
            R.id.key_up_arrow -> KeyEvent.KEYCODE_DPAD_UP
            else -> null
        }
        Log.d(tag, "Key pressed $keyCode")
        if (keyCode != null) {
            keyboardWithPointer.sendKeyDown(isCtrl = false, isShift = false, isAlt = false, keyCode = keyCode)
            keyboardWithPointer.sendKeyUp(isCtrl = false, isShift = false, isAlt = false, keyCode)
        }
    }

    private val btReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                    BluetoothAdapter.STATE_OFF -> {
                        disableInputs()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        setStatusText()
                    }
                }
            }
        }
    }

    override fun permissionsResolved(granted: Boolean) {
        if (granted) {
            bindService(Intent(this, BluetoothFacadeService::class.java), serviceConnection, BIND_AUTO_CREATE)
        } else {
            val statusTextView = findViewById<TextView>(R.id.status_text)
            statusTextView.text = getString(R.string.permission_denied)
        }
    }
}