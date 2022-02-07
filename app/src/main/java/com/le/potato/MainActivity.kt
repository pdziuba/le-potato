package com.le.potato

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.le.potato.transport.BluetoothFacadeService
import com.le.potato.transport.DeviceConnectedListener
import com.le.potato.utils.BluetoothEnabler
import kotlin.math.max


class MainActivity : AppCompatActivity(), View.OnKeyListener, DeviceConnectedListener {
    private val tag = MainActivity::class.java.simpleName
    private var bluetoothService: BluetoothFacadeService? = null
    private val keyboardWithPointer: KeyboardWithPointer = KeyboardWithPointer(this)
    private var requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it) {
                Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT)
                finish()
            }
        }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
            val binder = _binder as BluetoothFacadeService.LocalBinder
            val service = binder.getService()

            bluetoothService = service
            val devices = service.devices
            if (devices.isNotEmpty()) {
                onDeviceConnected(devices.first())
            }
            service.registerDeviceConnectedListener(this@MainActivity)
            keyboardWithPointer.hidTransport = service
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            keyboardWithPointer.hidTransport = null
        }
    }

    private val touchListener = object : View.OnTouchListener {
        var maxPointerCount: Int = 0
        var X: Float = 0.0F
        var Y: Float = 0.0F
        var firstX: Float = 0.0F
        var firstY: Float = 0.0F

        @SuppressLint("ClickableViewAccessibility")
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
                    mouse.movePointer(
                        (motionEvent.x - X).toInt(),
                        (motionEvent.y - Y).toInt(),
                        0,
                        leftButton = false,
                        rightButton = false,
                        middleButton = false
                    )
                    X = motionEvent.x
                    Y = motionEvent.y
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    X = motionEvent.x
                    Y = motionEvent.y
                    if ((X - firstX) * (X - firstX) + (Y - firstY) * (Y - firstY) < 20) {
                        val leftButton = maxPointerCount == 1
                        val middleButton = maxPointerCount == 2
                        val rightButton = maxPointerCount > 2

                        mouse.movePointer(
                            (motionEvent.x - X).toInt(),
                            (motionEvent.y - Y).toInt(),
                            0,
                            leftButton = leftButton,
                            rightButton = rightButton,
                            middleButton = middleButton
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val keyboardButton = findViewById<Button>(R.id.keyboard_button)
        keyboardButton.setOnKeyListener(this)
        keyboardButton.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
            }
        }
        findViewById<TextView>(R.id.touchpad).setOnTouchListener(touchListener)

        askForPermissions()
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter?.isEnabled == false) {
            BluetoothEnabler.enableBluetoothOrFinish(this)
        }
        val intent = Intent(this, BluetoothFacadeService::class.java)

        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService?.unregisterDeviceConnectedListener(this)
        unbindService(serviceConnection)
    }

    private fun askForPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, BLUETOOTH_ADVERTISE) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, BLUETOOTH_ADMIN) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(BLUETOOTH_ADMIN)
            }
            if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(ACCESS_FINE_LOCATION)
            }
        }
    }

    fun sendText(view: View) {
        if (view.hasFocus()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        } else if (!view.requestFocus()) {
            Log.e(tag, "Cannot acquire focus")
        }
    }

    fun showBluetoothStatus(view: View) {
        startActivity(Intent(view.context, BluetoothStatusActivity::class.java))
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            keyboardWithPointer.sendKeyDown(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, keyCode)
        } else {
            keyboardWithPointer.sendKeyUp()
        }
        return true
    }


    override fun onDeviceConnected(device: BluetoothDevice) {
        runOnUiThread {
            findViewById<TextView>(R.id.status_text).text = getString(R.string.status_connected, device.name)
            findViewById<Button>(R.id.keyboard_button).isEnabled = true
        }
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        runOnUiThread {
            findViewById<TextView>(R.id.status_text).text = getString(R.string.status_connecting)
        }
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        runOnUiThread {
            findViewById<TextView>(R.id.status_text).text = getString(R.string.status_idle)
            findViewById<Button>(R.id.keyboard_button).isEnabled = false
        }
    }
}