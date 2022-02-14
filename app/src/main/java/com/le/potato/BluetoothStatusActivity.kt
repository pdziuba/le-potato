package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.le.potato.bledevices.BLEDevicesListAdapter
import com.le.potato.bledevices.BTDeviceWrapper
import com.le.potato.transport.*
import com.le.potato.utils.BluetoothEnabler


class BluetoothStatusActivity : AppCompatActivity(), AdvertisingListener, DeviceConnectedListener,
    DeviceDiscoveryListener {
    private val tag = BluetoothStatusActivity::class.java.simpleName
    private var pairedDevicesListAdapter: BLEDevicesListAdapter? = null
    private var discoveredDevicesListAdapter: BLEDevicesListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothService: BluetoothFacadeService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, _binder: IBinder?) {
            val binder = _binder as BluetoothFacadeService.LocalBinder
            val service = binder.getService()
            service.advertisingListener = this@BluetoothStatusActivity
            service.registerDeviceConnectedListener(this@BluetoothStatusActivity)
            service.deviceDiscoveryListener = this@BluetoothStatusActivity
            service.init(this@BluetoothStatusActivity, KeyboardWithPointer.reportMap)
            bluetoothService = service
            loadBTDevicesState()

        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            bluetoothService = null
        }

    }

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
        pairedDevicesListAdapter = BLEDevicesListAdapter(this)
        discoveredDevicesListAdapter = BLEDevicesListAdapter(this)
        configureDevicesList(findViewById(R.id.paired_devices), pairedDevicesListAdapter!!)
        configureDevicesList(findViewById(R.id.discovered_devices), discoveredDevicesListAdapter!!)
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(btReceiver, intentFilter)
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BluetoothFacadeService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        loadBluetoothStatus()

        if (!bluetoothAdapter!!.isEnabled) {
            BluetoothEnabler.enableBluetoothOrFinish(this)
        }
    }

    private fun configureDevicesList(recyclerView: RecyclerView, adapter: BLEDevicesListAdapter) {
        adapter.clickListener = DeviceClickListener(adapter)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onDestroy() {
        bluetoothService?.unregisterDeviceConnectedListener(this)
        bluetoothService?.deviceDiscoveryListener = null
        bluetoothService?.advertisingListener = null
        unregisterReceiver(btReceiver)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        pairedDevicesListAdapter?.clear()
        discoveredDevicesListAdapter?.clear()
        findViewById<RecyclerView>(R.id.discovered_devices).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.discovered_devices_text).visibility = View.INVISIBLE
        unbindService(serviceConnection)
    }

    private fun loadBluetoothStatus() {
        findViewById<Button>(R.id.scan_button).isEnabled = bluetoothAdapter?.isEnabled == true
        findViewById<Button>(R.id.advertise_button).isEnabled = bluetoothAdapter?.isEnabled == true
        val bluetoothAdapter = bluetoothAdapter ?: return
        val deviceName = bluetoothAdapter.name
        findViewById<TextView>(R.id.device_name_text).text = deviceName
    }

    private fun loadBTDevicesState() {
        val bluetoothAdapter = bluetoothAdapter ?: return
        val bluetoothFacadeService = bluetoothService ?: return
        findViewById<TextView>(R.id.paired_devices_text).visibility = if (bluetoothAdapter.bondedDevices.isEmpty()) View.INVISIBLE else View.VISIBLE
        pairedDevicesListAdapter?.clear()
        for (device in bluetoothAdapter.bondedDevices) {
            val state = when {
                (bluetoothFacadeService.connectedDevice?.address == device.address) -> BluetoothProfile.STATE_CONNECTED
                (bluetoothFacadeService.connectingDevice?.address == device.address) -> BluetoothProfile.STATE_CONNECTING
                else -> BluetoothProfile.STATE_DISCONNECTED
            }
            pairedDevicesListAdapter?.addDevice(BTDeviceWrapper(device, state))
        }

        if (bluetoothFacadeService.isAdvertising) {
            onAdvertiseStarted()
        } else if (bluetoothFacadeService.isScanning) {
            onDiscoveryStarted()
        }
        setStatusText()
    }

    private fun setStatusText() {
        val statusTextView = findViewById<TextView>(R.id.status_text2)
        StatusMixin.setStatusText(statusTextView, bluetoothService, bluetoothAdapter, this)
    }

    inner class DeviceClickListener(private val adapter: BLEDevicesListAdapter) :
        BLEDevicesListAdapter.ItemClickListener {
        override fun onItemClick(view: View?, position: Int) {
            val deviceWrapper = adapter.getItem(position)
            when (deviceWrapper.connectionState) {
                BluetoothProfile.STATE_DISCONNECTED -> {
                    bluetoothService?.connectToDevice(deviceWrapper.device)
                }
                BluetoothProfile.STATE_CONNECTED -> {
                    bluetoothService?.disconnect(deviceWrapper.device)
                }
            }
        }

    }

    override fun onDeviceDetected(device: BluetoothDevice) {
        runOnUiThread {
            findViewById<RecyclerView>(R.id.discovered_devices).visibility = View.VISIBLE
            findViewById<TextView>(R.id.discovered_devices_text).visibility = View.VISIBLE
            discoveredDevicesListAdapter?.addDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
        }
    }

    override fun onDiscoveryStarted() {
        runOnUiThread {
            findViewById<Button>(R.id.scan_button).text = getString(R.string.stop)
            findViewById<ProgressBar>(R.id.scan_spinner).visibility = View.VISIBLE
            setAdvertisingEnabled(false)
            setStatusText()
        }
    }

    override fun onDiscoveryStopped() {
        runOnUiThread {
            findViewById<Button>(R.id.scan_button).text = getString(R.string.scan)
            findViewById<ProgressBar>(R.id.scan_spinner).visibility = View.INVISIBLE
            setAdvertisingEnabled(true)
            setStatusText()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScanButtonClicked(view: View?) {
        bluetoothService?.startScanning()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAdvertiseButtonClicked(view: View?) {
        val service = bluetoothService ?: return

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
            setStatusText()
        }
    }

    override fun onAdvertiseStopped() {
        runOnUiThread {
            findViewById<ProgressBar>(R.id.advertise_spinner).visibility = View.INVISIBLE
            findViewById<Button>(R.id.advertise_button).text = getString(R.string.advertise)
            setScanningEnabled(true)
            setStatusText()
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

    private fun setAdvertisingEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.advertise_button).isEnabled = enabled
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        runOnUiThread {
            Log.d(tag, "onDeviceConnected ${device.address}")
            if (pairedDevicesListAdapter?.hasDevice(device.address) == true) {
                pairedDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTED))
            } else {
                discoveredDevicesListAdapter?.removeDevice(device.address)
                pairedDevicesListAdapter?.addDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTED))
            }
            setStatusText()
        }
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        runOnUiThread {
            Log.d(tag, "onDeviceConnecting ${device.address}")
            if (pairedDevicesListAdapter?.hasDevice(device.address) == true) {
                pairedDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTING))
            } else {
                discoveredDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_CONNECTING))
            }
            setStatusText()
        }
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        runOnUiThread {
            Log.d(tag, "onDeviceDisconnected ${device.address}")
            pairedDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
            setStatusText()
        }
    }

    override fun onDeviceConnectionError(device: BluetoothDevice, error: String?) {
        runOnUiThread {
            if (error == null) {
                Toast.makeText(this, getString(R.string.connection_error), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.connection_error_details, error), Toast.LENGTH_LONG).show()
            }
            pairedDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
            discoveredDevicesListAdapter?.updateDevice(BTDeviceWrapper(device, BluetoothProfile.STATE_DISCONNECTED))
            setStatusText()
        }
    }

    private val btReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                    BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_OFF -> {
                        loadBluetoothStatus()
                        loadBTDevicesState()
                    }
                }
            }
        }
    }
}