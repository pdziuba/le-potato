package com.example.myapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.TextView

class BluetoothStatusActivity : AppCompatActivity() {
    private val tag = "BluetoothStatusActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_status)
        loadDeviceStatus()
    }

    private fun loadDeviceStatus() {
        val isBluetoothSupported = isBluetoothSupported()
        val isBluetoothEnabled = isBluetoothEnabled()
        val isBLESupported = isBleSupported()
        val deviceName = if (isBluetoothSupported) getDeviceName() else ""
        findViewById<CheckBox>(R.id.bluetooth_available_checkbox).isChecked = isBluetoothSupported
        findViewById<CheckBox>(R.id.bluetooth_on_checkbox).isChecked = isBluetoothEnabled
        findViewById<CheckBox>(R.id.ble_checkbox).isChecked = isBLESupported
        findViewById<TextView>(R.id.device_name_text_view).text = deviceName

    }

    private fun getDeviceName(): String {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter.name
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? =
            bluetoothManager.adapter
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.isEnabled
        }
        return false
    }

    private fun isBluetoothSupported(): Boolean {
        try {
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                return false
            }
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter: BluetoothAdapter? =
                bluetoothManager.adapter
            if (bluetoothAdapter != null) {
                return true
            }
        } catch (error: Throwable) {
            Log.e(tag, "Exception when trying to check bluetooth support ${error.localizedMessage}")
        }
        return false
    }

    private fun isBleSupported(): Boolean {
        try {
            return isBluetoothSupported() && packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } catch (error: Throwable) {
            Log.e(tag, "Exception when trying to check BLE support ${error.localizedMessage}")
        }
        return false
    }
}