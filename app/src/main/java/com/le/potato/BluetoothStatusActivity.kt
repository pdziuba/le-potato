package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.le.potato.bledevices.BLEDevicesListAdapter


class BluetoothStatusActivity : AppCompatActivity(),
    BLEDevicesListAdapter.ItemClickListener, DeviceDetectedListener {
    private val tag = "BluetoothStatusActivity"
    private var bleDevicesListAdapter: BLEDevicesListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleService: BLEService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_status)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "No bluetooth on board found :(", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val recyclerView: RecyclerView = findViewById(R.id.ble_devices)
        bleDevicesListAdapter = BLEDevicesListAdapter(this)
        bleDevicesListAdapter?.clickListener = this
        recyclerView.adapter = bleDevicesListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        val intent = Intent(this, BLEService::class.java)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
                Log.d(tag, "connecting service")
                val binder = _binder as BLEService.LocalBinder
                val service = binder.getService()
                service.deviceDetectedListener = this@BluetoothStatusActivity
                bleService = service

            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                bleService = null
            }

        }, BIND_AUTO_CREATE)

    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null) {
            // world cornered us again
            finish()
            return
        }
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter!!.isEnabled) {
            BluetoothHelper.enableBluetoothOrFinish(this)
        }
        else {
            loadBluetoothStatus()
        }
    }

    private fun loadBluetoothStatus() {
        val bluetoothAdapter = bluetoothAdapter ?: return

        val isBluetoothSupported = true
        val isBluetoothEnabled = bluetoothAdapter.isEnabled
        val isBLESupported =
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val deviceName = bluetoothAdapter.name
        findViewById<CheckBox>(R.id.bluetooth_available_checkbox).isChecked =
            isBluetoothSupported
        findViewById<CheckBox>(R.id.bluetooth_on_checkbox).isChecked = isBluetoothEnabled
        findViewById<CheckBox>(R.id.ble_checkbox).isChecked = isBLESupported
        findViewById<TextView>(R.id.device_name_text_view).text = deviceName
        for (device in bluetoothAdapter.bondedDevices) {
            bleDevicesListAdapter?.addDevice(device)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        bleService?.stopScanning()
        bleService?.deviceDetectedListener = null
    }

    override fun onItemClick(view: View?, position: Int) {
        Toast.makeText(this, bleDevicesListAdapter?.getItem(position)?.name, Toast.LENGTH_SHORT).show()
        bleService?.connectToDevice(bleDevicesListAdapter?.getItem(position))
    }

    override fun onDeviceDetected(device: BluetoothDevice) {
        runOnUiThread {
            bleDevicesListAdapter?.addDevice(device)
        }
    }

    fun onScanButtonClicked(view: View?) {
        bleService?.startScanning()
    }
}