package com.le.potato.transport

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import java.util.*
import kotlin.concurrent.schedule

const val SERVICE_TERMINATION_DELAY = 120000L

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
    private var preferences: SharedPreferences? = null
    private var terminationTimer: Timer? = null

    companion object {
        const val LAST_CONNECTED_DEVICE = "last_connected_device"
        const val LAST_ACTIVE_TRANSPORT = "last_active_transport"
    }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(tag, "All service clients unbound, scheduling termination of the service")
        terminationTimer = Timer()
        terminationTimer?.schedule(SERVICE_TERMINATION_DELAY) {
            this@BluetoothFacadeService.stopSelf()
        }

        return true
    }

    override fun onRebind(intent: Intent?) {
        Log.i(tag, "New client bound, canceling termination")
        terminationTimer?.cancel()
        terminationTimer = null
    }

    private inner class ConnectionListener(val source: HIDTransport) : DeviceConnectedListener {
        override fun onDeviceConnected(device: BluetoothDevice) {
            activeTransport = source
            _connectingDevice = null
            _connectedDevice = device
            fireDeviceConnectedEvent(device)
            saveLastConnectedDevice()
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

    private fun saveLastConnectedDevice() {
        val lastConnectedDevice = connectedDevice ?: return
        val lastActiveTransport = activeTransport ?: return
        val settings = preferences ?: return
        with(settings.edit()) {
            putString(LAST_CONNECTED_DEVICE, lastConnectedDevice.address)
            if (lastActiveTransport === bleTransport) {
                putString(LAST_ACTIVE_TRANSPORT, "BLE")
            } else {
                putString(LAST_ACTIVE_TRANSPORT, "CLASSIC")
            }
            apply()
        }
    }

    private fun eraseLastConnectedDevice() {
        val settings = preferences ?: return
        with(settings.edit()) {
            remove(LAST_CONNECTED_DEVICE)
            remove(LAST_ACTIVE_TRANSPORT)
            apply()
        }
    }

    private fun reconnectToLastDevice() {
        val settings = preferences ?: return
        val lastConnectedDeviceAddr = settings.getString(LAST_CONNECTED_DEVICE, null) ?: return
        val lastActiveTransport = settings.getString(LAST_ACTIVE_TRANSPORT, null) ?: return
        Handler(applicationContext.mainLooper).postDelayed({
            if (connectedDevice != null) {
                return@postDelayed
            }
            if (lastActiveTransport == "BLE") {
                startAdvertising()
            } else {
                val lastConnectedDevice = bluetoothAdapter?.getRemoteDevice(lastConnectedDeviceAddr) ?: return@postDelayed
                connectToDevice(lastConnectedDevice)
            }
        }, 500)

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
        preferences = applicationContext.getSharedPreferences(applicationInfo.packageName, Context.MODE_PRIVATE)
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
        val canInitialize = bluetoothAdapter!!.isEnabled
        if (!initialized && canInitialize) {
            Log.d(tag, "Init called")
            bleTransport.init(context, reportMap)
            classicTransport.init(context, reportMap)
            initialized = true
            reconnectToLastDevice()
        }
    }

    override fun addInputReport(reportId: Byte, report: ByteArray) {
        activeTransport?.addInputReport(reportId, report)
    }

    override fun disconnect(device: BluetoothDevice) {
        activeTransport?.disconnect(device)
        eraseLastConnectedDevice()
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
