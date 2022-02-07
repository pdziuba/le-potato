package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.le.potato.bledevices.BLEDevicesListAdapter
import com.le.potato.bledevices.BTDeviceWrapper
import com.le.potato.transport.AdvertisingListener
import com.le.potato.transport.BluetoothFacadeService
import com.le.potato.transport.DeviceConnectedListener
import com.le.potato.utils.BluetoothEnabler


class BluetoothStatusActivity : AppCompatActivity(),
    BLEDevicesListAdapter.ItemClickListener, AdvertisingListener, DeviceConnectedListener {
    private var bleDevicesListAdapter: BLEDevicesListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothFacadeService: BluetoothFacadeService? = null

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
        val recyclerView: RecyclerView = findViewById(R.id.paired_devices)
        bleDevicesListAdapter = BLEDevicesListAdapter(this)
        bleDevicesListAdapter?.clickListener = this
        recyclerView.adapter = bleDevicesListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        val intent = Intent(this, BluetoothFacadeService::class.java)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
                val binder = _binder as BluetoothFacadeService.LocalBinder
                val service = binder.getService()
                service.advertisingListener = this@BluetoothStatusActivity
                service.registerDeviceConnectedListener(this@BluetoothStatusActivity)
                bluetoothFacadeService = service
                loadBTDevicesState()

            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                bluetoothFacadeService = null
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
            BluetoothEnabler.enableBluetoothOrFinish(this)
        } else {
            loadBluetoothStatus()
            loadBTDevicesState()
        }
    }

    override fun onStop() {
        super.onStop()
        bleDevicesListAdapter?.clear()
    }

    private fun loadBluetoothStatus() {
        val bluetoothAdapter = bluetoothAdapter ?: return
        val isBluetoothEnabled = bluetoothAdapter.isEnabled
        val deviceName = bluetoothAdapter.name
        findViewById<CheckBox>(R.id.bluetooth_on_checkbox).isChecked = isBluetoothEnabled
        findViewById<TextView>(R.id.device_name_text).text = deviceName
    }

    private fun loadBTDevicesState() {
        val bluetoothAdapter = bluetoothAdapter ?: return
        val bluetoothFacadeService = bluetoothFacadeService?: return
        for (device in bluetoothAdapter.bondedDevices) {
            val state = when {
                (bluetoothFacadeService.connectedDevice?.address == device.address) -> BluetoothProfile.STATE_CONNECTED
                (bluetoothFacadeService.connectingDevice?.address == device.address) -> BluetoothProfile.STATE_CONNECTING
                else -> BluetoothProfile.STATE_DISCONNECTED
            }
            bleDevicesListAdapter?.addDevice(BTDeviceWrapper(device, state))
        }

        if (bluetoothFacadeService.isAdvertising) {
            onAdvertiseStarted()
        }
    }

    override fun onItemClick(view: View?, position: Int) {
        val deviceWrapper = bleDevicesListAdapter?.getItem(position) ?: return
        when (deviceWrapper.connectionState) {
            BluetoothProfile.STATE_DISCONNECTED -> {bluetoothFacadeService?.connectToDevice(deviceWrapper.device)}
            BluetoothProfile.STATE_CONNECTED -> {bluetoothFacadeService?.disconnect(deviceWrapper.device)}
        }
    }

    fun onDeviceDetected(device: BluetoothDevice) {
        runOnUiThread {
            bleDevicesListAdapter?.addDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
        }
    }

    fun onScanButtonClicked(view: View?) {
        bluetoothFacadeService?.startScanning()
    }

    fun onAdvertiseButtonClicked(view: View?) {
        val service = bluetoothFacadeService ?: return

        if (service.isAdvertising) {
            service.stopAdvertising()
        } else {
            service.startAdvertising()
        }
    }

    override fun onAdvertiseStarted() {
        runOnUiThread {
            findViewById<ProgressBar>(R.id.advertise_spinner).visibility = View.VISIBLE
            findViewById<Button>(R.id.advertise_button).text = getString(R.string.stop)
            setScanningEnabled(false)
        }
    }

    override fun onAdvertiseStopped() {
        runOnUiThread {
            findViewById<ProgressBar>(R.id.advertise_spinner).visibility = View.INVISIBLE
            findViewById<Button>(R.id.advertise_button).text = getString(R.string.advertise)
            setScanningEnabled(true)
        }
    }

    override fun onAdvertiseFailure() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.advertising_error), Toast.LENGTH_SHORT).show()
            findViewById<Button>(R.id.advertise_button).text = getString(R.string.advertise)
            setScanningEnabled(true)
        }
    }

    private fun setScanningEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.scan_button).isEnabled = enabled
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        bleDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTED))
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        bleDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTING))
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        bleDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
    }
}