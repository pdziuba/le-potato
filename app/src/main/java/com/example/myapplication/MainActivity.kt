package com.example.myapplication

import android.Manifest.permission.*
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
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity(), View.OnKeyListener, DeviceConnectedListener {
    private val tag = "MainActivity"
    private var bleService: BLEService? = null
    private var keyboardPeripheral: KeyboardPeripheral? = null
    private var requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it) {
                Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT)
                finish()
            }
        }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
            val binder = _binder as BLEService.LocalBinder
            val service = binder.getService()
            keyboardPeripheral = service.keyboard
            service.deviceConnectedListener = this@MainActivity
            bleService = service
            val devices = service.devices
            if (devices.isNotEmpty()) {
                onDeviceConnected(devices.first())
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            keyboardPeripheral = null
            bleService?.deviceConnectedListener = null
        }

    }
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
        askForPermissions()
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter?.isEnabled == false) {
            BluetoothHelper.enableBluetoothOrFinish(this)
        }
        val intent = Intent(this, BLEService::class.java)

        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleService?.deviceConnectedListener = null
        unbindService(connection)
        keyboardPeripheral = null
    }

    private fun askForPermissions() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_DENIED) {
            requestPermissionsLauncher.launch(ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) == PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestPermissionsLauncher.launch(ACCESS_BACKGROUND_LOCATION)
            }
        }
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
        startActivity(Intent(this, BluetoothStatusActivity::class.java))
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        // todo: simplify this
        Log.d(tag, "Received event ${event.toString()} with keycode $keyCode")
        val keyCodeUS: Int = KeyboardPeripheral.keyCode(event?.unicodeChar?.toChar().toString()).toInt()
        if (event?.action == KeyEvent.ACTION_DOWN) {
            if (keyCodeUS == 0) {
                Log.e(tag, "Kurwa mać")
                return false
            }
            var modifier = 0

            if (event.isAltPressed) {
                modifier = modifier or KeyboardPeripheral.MODIFIER_KEY_ALT
            }
            if (event.isShiftPressed) {
                modifier = modifier or KeyboardPeripheral.MODIFIER_KEY_SHIFT
            }
            if (event.isCtrlPressed) {
                modifier = modifier or KeyboardPeripheral.MODIFIER_KEY_CTRL
            }
            keyboardPeripheral?.sendKeyDown(modifier.toByte(), keyCodeUS.toByte())
        } else {
            keyboardPeripheral?.sendKeyUp()
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