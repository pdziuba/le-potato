package com.example.myapplication

import android.Manifest.permission.BLUETOOTH_ADMIN
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity(), View.OnKeyListener {
    private val tag = "MainActivity"
    private var keyboardPeripheral: KeyboardPeripheral? = null
    private var requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it) {
                Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.keyboard_button).setOnKeyListener(this)
        askForPermissions()
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter?.isEnabled == false) {
            BluetoothHelper.enableBluetoothOrFinish(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter?.isEnabled == true && keyboardPeripheral == null) {
            keyboardPeripheral = KeyboardPeripheral(this)
//            keyboardPeripheral?.setDeviceName("BLERC Keyboard")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        keyboardPeripheral?.stopAdvertising()
    }

    private fun askForPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, BLUETOOTH_ADVERTISE) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, BLUETOOTH_ADMIN) == PERMISSION_DENIED) {
                requestPermissionsLauncher.launch(BLUETOOTH_ADMIN)
            }
        }
    }

    fun sendText(view: View) {
        if (view.requestFocus()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        } else {
            Log.e(tag, "Cannot acquire focus")
        }
    }

    fun showBluetoothStatus(view: View) {
        startActivity(Intent(this, BluetoothStatusActivity::class.java))
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(tag, "Received event ${event.toString()} with keycode $keyCode")
        val keyCodeUS: Int = KeyboardPeripheral.keyCode(event?.unicodeChar?.toChar().toString()).toInt()
        if (keyCodeUS != 0 && event?.action == KeyEvent.ACTION_DOWN) {
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

//    override fun onDeviceConnected(device: BluetoothDevice) {
//        runOnUiThread {
//            Toast.makeText(this, "Device ${device.name} connected!", Toast.LENGTH_SHORT).show()
//        }
//
//    }
//
//    override fun onDeviceDisconnected(device: BluetoothDevice) {
//        runOnUiThread {
//            Toast.makeText(this, "Device ${device.name} disconnected!", Toast.LENGTH_SHORT).show()
//        }
//    }
}