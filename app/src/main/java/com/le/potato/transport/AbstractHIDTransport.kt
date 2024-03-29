package com.le.potato.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import java.util.*

open class DeviceConnectedSubject: DeviceConnectionObservable {
    private val deviceConnectedListeners: MutableSet<DeviceConnectedListener> = HashSet()

    override fun registerDeviceConnectedListener(deviceConnectedListener: DeviceConnectedListener) {
        deviceConnectedListeners.add(deviceConnectedListener)
    }

    override fun unregisterDeviceConnectedListener(deviceConnectedListener: DeviceConnectedListener) {
        deviceConnectedListeners.remove(deviceConnectedListener)
    }

    override fun fireDeviceConnectedEvent(device: BluetoothDevice) {
        deviceConnectedListeners.forEach { it.onDeviceConnected(device) }
    }

    override fun fireDeviceDisconnectedEvent(device: BluetoothDevice) {
        deviceConnectedListeners.forEach { it.onDeviceDisconnected(device) }
    }

    override fun fireDeviceConnectingEvent(device: BluetoothDevice) {
        deviceConnectedListeners.forEach { it.onDeviceConnecting(device) }
    }

    override fun fireDeviceConnectionErrorEvent(device: BluetoothDevice, error: String?) {
        deviceConnectedListeners.forEach { it.onDeviceConnectionError(device, error) }
    }
}

abstract class AbstractHIDTransport(subject: DeviceConnectedSubject = DeviceConnectedSubject()): HIDTransport, DeviceConnectionObservable by subject {
    protected var applicationContext: Context? = null
    protected var reportMap: ByteArray? = null
    protected var bluetoothAdapter: BluetoothAdapter? = null
    protected var bluetoothManager: BluetoothManager? = null
    protected var handler: Handler? = null
    protected val connectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()


    override fun init(context: Context, reportMap: ByteArray) {
        this.applicationContext = context
        this.reportMap = reportMap
        handler = Handler(context.mainLooper)
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager!!.adapter
            ?: throw UnsupportedOperationException("Bluetooth is not available.")
    }

    override val devices: Set<BluetoothDevice>
        get() {
            val deviceSet: MutableSet<BluetoothDevice> = HashSet()
            synchronized(connectedDevicesMap) { deviceSet.addAll(connectedDevicesMap.values) }
            return Collections.unmodifiableSet(deviceSet)
        }
}