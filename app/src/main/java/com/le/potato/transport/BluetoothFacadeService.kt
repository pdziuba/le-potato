package com.le.potato.transport

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log


class BluetoothFacadeService(subject: DeviceConnectedSubject = DeviceConnectedSubject()) : Service(), HIDTransport,
    DeviceConnectionObservable by subject {
    private val tag = BluetoothFacadeService::class.java.simpleName
    private var reportMap: ByteArray? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val bleTransport = BLETransport()
    private val classicTransport = BluetoothClassicTransport()
    private var activeTransport: HIDTransport? = null
    private val bleListener = ConnectionListener(bleTransport)
    private val classicListener = ConnectionListener(classicTransport)
    private var initialized = false
    private var _connectedDevice: BluetoothDevice? = null
    private var _connectingDevice: BluetoothDevice? = null

    var deviceDiscoveryListener: DeviceDiscoveryListener?
        get() = classicTransport.deviceDiscoveryListener
        set(value) {
            classicTransport.deviceDiscoveryListener = value
        }

    val connectedDevice: BluetoothDevice?
        get() = _connectedDevice

    val connectingDevice: BluetoothDevice?
        get() = _connectingDevice

    val isScanning: Boolean
        get() = classicTransport.isScanning

    val isAdvertising: Boolean
        get() = bleTransport.isAdvertising

    override val devices: Set<BluetoothDevice>
        get() = activeTransport?.devices ?: setOf()

    var advertisingListener: AdvertisingListener?
        get() = bleTransport.advertisingListener
        set(value) {
            bleTransport.advertisingListener = value
        }

    private inner class ConnectionListener(val source: HIDTransport) : DeviceConnectedListener {
        override fun onDeviceConnected(device: BluetoothDevice) {
            activeTransport = source
            _connectingDevice = null
            _connectedDevice = device
            fireDeviceConnectedEvent(device)
        }

        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectingDevice = device
            fireDeviceConnectingEvent(device)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            if (activeTransport == source || _connectingDevice == device) {
                activeTransport = null
                _connectingDevice = null
                _connectedDevice = null
                fireDeviceDisconnectedEvent(device)
            }
        }

        override fun onDeviceConnectionError(device: BluetoothDevice, error: String?) {
            _connectingDevice = null
            fireDeviceConnectionErrorEvent(device, error)
        }
    }

    override fun onCreate() {
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
            ?: throw UnsupportedOperationException("Bluetooth is not available.")
        bleTransport.registerDeviceConnectedListener(bleListener)
        classicTransport.registerDeviceConnectedListener(classicListener)
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        applicationContext.registerReceiver(btReceiver, intentFilter)
    }

    private val btReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                Log.d(tag, "Bluetooth adapter state changed to $newState")
                when (newState) {
                    BluetoothAdapter.STATE_TURNING_OFF -> deactivate()
                    BluetoothAdapter.STATE_ON -> init(applicationContext, reportMap!!)
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothFacadeService {
            return this@BluetoothFacadeService
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return LocalBinder()
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy called, deactivating transports")
        bleTransport.unregisterDeviceConnectedListener(bleListener)
        classicTransport.unregisterDeviceConnectedListener(classicListener)
        deactivate()
        applicationContext.unregisterReceiver(btReceiver)
    }

    override fun init(context: Context, reportMap: ByteArray) {
        this.reportMap = reportMap
        if (!initialized && bluetoothAdapter!!.isEnabled) {
            Log.d(tag, "Init called")
            bleTransport.init(context, reportMap)
            classicTransport.init(context, reportMap)
            initialized = true
        }
    }

    override fun addInputReport(reportId: Int, report: ByteArray) {
        activeTransport?.addInputReport(reportId, report)
    }

    override fun disconnect(device: BluetoothDevice) {
        activeTransport?.disconnect(device)
    }

    override fun deactivate() {
        if (initialized) {
            bleTransport.deactivate()
            classicTransport.deactivate()
            initialized = false
        }
    }

    fun startScanning() {
        classicTransport.startScanning()
    }

    fun stopScanning() {
        classicTransport.stopScanning()
    }

    fun startAdvertising() {
        bleTransport.startAdvertising()
    }

    fun stopAdvertising() {
        bleTransport.stopAdvertising()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (connectedDevice?.address == device.address || connectingDevice?.address == device.address) return
        if (activeTransport != null && connectedDevice != null) {
            // todo: this is dirty :(
            val subject: DeviceConnectedSubject = activeTransport as DeviceConnectedSubject
            // wait until active device is disconnected then create new connection
            subject.registerDeviceConnectedListener(object : DeviceConnectedListener {
                override fun onDeviceConnected(device: BluetoothDevice) {}

                override fun onDeviceConnecting(device: BluetoothDevice) {}

                override fun onDeviceDisconnected(device: BluetoothDevice) {
                    subject.unregisterDeviceConnectedListener(this)
                    classicTransport.connectToDevice(device)
                }

                override fun onDeviceConnectionError(device: BluetoothDevice, error: String?) {}

            })
            activeTransport?.disconnect(connectedDevice!!)
        } else {
            classicTransport.connectToDevice(device)
        }
    }

}
