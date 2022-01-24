package com.example.myapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.bledevices.BLEDevicesListAdapter


class BluetoothStatusActivity : AppCompatActivity(),
    BLEDevicesListAdapter.ItemClickListener {
    private var bleDevicesListAdapter: BLEDevicesListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

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

    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null) {
            // fuck this
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

    override fun onPause() {
        super.onPause()
        bleDevicesListAdapter?.clear()
    }

    private fun loadBluetoothStatus() {
        if (bluetoothAdapter != null) {
            val isBluetoothSupported = true
            val isBluetoothEnabled = bluetoothAdapter!!.isEnabled
            val isBLESupported =
                packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            val deviceName = bluetoothAdapter!!.name
            findViewById<CheckBox>(R.id.bluetooth_available_checkbox).isChecked =
                isBluetoothSupported
            findViewById<CheckBox>(R.id.bluetooth_on_checkbox).isChecked = isBluetoothEnabled
            findViewById<CheckBox>(R.id.ble_checkbox).isChecked = isBLESupported
            findViewById<TextView>(R.id.device_name_text_view).text = deviceName
        }
    }


    override fun onItemClick(view: View?, position: Int) {
        Toast.makeText(this, bleDevicesListAdapter?.getItem(position), Toast.LENGTH_SHORT).show()
    }
}