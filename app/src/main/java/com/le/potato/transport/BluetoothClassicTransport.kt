package com.le.potato.transport

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.le.potato.utils.bondStateLabels
import com.le.potato.utils.connStateLabels
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


interface DeviceDiscoveryListener {
    fun onDeviceDetected(device: BluetoothDevice)
    fun onDiscoveryStarted()
    fun onDiscoveryStopped()
}

class BluetoothClassicTransport : AbstractHIDTransport() {
    private val tag = BluetoothClassicTransport::class.java.simpleName
    private var connectingDevice: BluetoothDevice? = null
    private var hidDevice: BluetoothHidDevice? = null
    var deviceDiscoveryListener: DeviceDiscoveryListener? = null
    private val inputReportQueue: Queue<Pair<Byte, ByteArray>> = ConcurrentLinkedQueue()
    private var reportingTimer: Timer? = null
    private var connectionTimeout: Timer? = null
    private var hidProfileRegistered: Boolean = false

    val isScanning: Boolean
        get() = bluetoothAdapter?.isDiscovering ?: false

    private fun finalizeConnection(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.put(device.address, device)
        }
        connectingDevice = null
        connectionTimeout?.cancel()
        connectionTimeout = null
        fireDeviceConnectedEvent(device)
    }

    override fun init(context: Context, reportMap: ByteArray) {
        super.init(context, reportMap)
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(btReceiver, intentFilter)
        setupHIDProfile()
        startReportingNotifications()
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice? ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, Integer.MIN_VALUE)
                    Log.d(
                        tag,
                        "BOND STATE = ${bondStateLabels[bondState]} device = ${bluetoothDevice.address} connecting device = ${connectingDevice?.address}"
                    )
                    if (bondState in intArrayOf(
                            BluetoothDevice.BOND_BONDED,
                            BluetoothDevice.BOND_BONDING
                        ) && bluetoothDevice == connectingDevice
                    ) {
                        finalizeConnection(bluetoothDevice)
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val bluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice? ?: return
                    deviceDiscoveryListener?.onDeviceDetected(bluetoothDevice)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    deviceDiscoveryListener?.onDiscoveryStarted()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    deviceDiscoveryListener?.onDiscoveryStopped()
                }
                else -> {
                    Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
                }
            }
        }
    }

    val classicHidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            hidProfileRegistered = registered
            Log.i(tag, "ClassicHID app status changed device is $pluggedDevice registered $registered")

        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (device == null) return
            Log.i(tag, "ClassicHID connection state changed state = ${connStateLabels[state]}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        finalizeConnection(device)
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    fireDeviceConnectingEvent(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.remove(device.address)
                    }
                    fireDeviceDisconnectedEvent(device)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.i(tag, "ClassicHID onGetReport")
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            super.onSetReport(device, type, id, data)
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            super.onSetProtocol(device, protocol)
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            super.onInterruptData(device, reportId, data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
        }
    }

    override fun addInputReport(reportId: Byte, report: ByteArray) {
        inputReportQueue.offer(Pair(reportId, report))
    }

    override fun disconnect(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.remove(device.address)
        }
        if (hidDevice?.getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
            hidDevice?.disconnect(device)
        }
        fireDeviceDisconnectedEvent(device)

    }

    override fun deactivate() {
        stopScanning()
        val hidDevice = hidDevice ?: return
        handler?.post {
            for (device in hidDevice.connectedDevices) {
                hidDevice.disconnect(device)
            }
            synchronized(connectedDevicesMap) {
                connectedDevicesMap.clear()
            }
            hidDevice.unregisterApp()
        }
        reportingTimer?.cancel()
        applicationContext?.unregisterReceiver(btReceiver)
    }

    override fun fireDeviceConnectionErrorEvent(device: BluetoothDevice, error: String?) {
        super.fireDeviceConnectionErrorEvent(device, error)
        connectingDevice = null
        connectionTimeout?.cancel()
        connectionTimeout = null
    }

    override fun fireDeviceDisconnectedEvent(device: BluetoothDevice) {
        super.fireDeviceDisconnectedEvent(device)
        connectingDevice = null
        connectionTimeout?.cancel()
        connectionTimeout = null
    }

    fun connectToDevice(device: BluetoothDevice?) {
        if (device == null || connectingDevice != null) {
            return
        }
        val hidDevice = hidDevice
        if (hidDevice == null) {
            fireDeviceConnectionErrorEvent(device, "HID Device not initialized")
            return
        }
        if (!hidProfileRegistered) {
            fireDeviceConnectionErrorEvent(device, "HID Profile not registered")
            return
        }
        synchronized(connectedDevicesMap) {
            if (connectedDevicesMap.containsKey(device.address)) {
                Log.d(tag, "Already connected. Skipping.")
                return
            }
        }
        handler?.post {
            for (connectedDevice in hidDevice.connectedDevices) {
                if (connectedDevice.address != device.address) {
                    hidDevice.disconnect(connectedDevice)
                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.remove(connectedDevice.address)
                    }
                    fireDeviceDisconnectedEvent(connectedDevice)
                }
            }
            connectingDevice = device
            fireDeviceConnectingEvent(device)
            stopScanning()
            if (!hidDevice.connect(device)) {
                fireDeviceConnectionErrorEvent(device)
            } else {
                connectionTimeout = Timer()
                connectionTimeout!!.schedule(object : TimerTask() {
                    override fun run() {
                        Log.e(tag, "Connection time out, firing error event")
                        fireDeviceConnectionErrorEvent(device, "Connection timeout")
                    }
                }, 30000L)
            }
        }
    }

    private fun setupHIDProfile() {
        if (hidDevice != null) {
            throw IllegalStateException("HID Device seems to be already registered")
        }
        Log.d(tag, "Setting up HID profile")
        handler?.post {
            bluetoothAdapter?.getProfileProxy(applicationContext, hidServiceListener(), BluetoothProfile.HID_DEVICE)
        }
    }

    private fun hidServiceListener() = object : BluetoothProfile.ServiceListener {

        override fun onServiceConnected(status: Int, btProfile: BluetoothProfile?) {
            val hidProfile: BluetoothHidDevice = btProfile as BluetoothHidDevice
            hidDevice = hidProfile
            Log.i(tag, "Profile HID connected")
            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                "ELPotatoApp",
                "Virtual HID",
                "PrzemoDev",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                reportMap
            )
            val qusInSettings = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
                0,
                0,
                0,
                -1,
                -1
            )
            val qosOutSettings = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,
                9,
                0,
                11250,
                11250
            )
            handler?.post {
                hidProfile.registerApp(
                    sdpSettings,
                    qusInSettings,
                    qosOutSettings,
                    applicationContext!!.mainExecutor,
                    classicHidCallback
                )
            }
        }

        override fun onServiceDisconnected(p0: Int) {
            Log.wtf(tag, "Service disconnected")
            hidDevice = null
        }

    }

    fun startScanning() {
        if (isScanning) {
            Log.d(tag, "Already scanning, skipping")
            return
        }

        handler?.post {
            Log.d(tag, "startScanning")
            if (!bluetoothAdapter!!.startDiscovery()) {
                Log.wtf(tag, "Start discovery failed!")
            }
        }
    }

    fun stopScanning() {
        // Calling bluetoothAdapter.cancelDiscovery tend to break Bluetooth on some devices.
        // Let's calm down and wait for this shit to finish
        while (isScanning) {
            Log.d(tag, "Good ol' sleep until discovery is not turned off")
            Thread.sleep(1000)
        }
    }

    private fun startReportingNotifications(dataSendingRate: Long = 15) {
        reportingTimer = Timer()
        // send report each dataSendingRate, if data available
        reportingTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {

                val polled = inputReportQueue.poll()
                if (polled != null) {
                    handler?.post {
                        val devices = devices
                        if (devices.isEmpty()) {
                            Log.d(tag, "No devices to send notification")
                        }
                        for (device in devices) {
                            try {
                                val result = hidDevice?.sendReport(device, polled.first.toInt(), polled.second)
                                if (result != true) {
                                    Log.e(tag, "Failed to send notification. Reason unknown.")
                                } else {
                                    Log.d(tag, "Notification sent")
                                }
                            } catch (e: Throwable) {
                                Log.e(tag, "Error on sending notification ${e.localizedMessage}")
                            }
                        }
                    }

                }
            }
        }, 0, dataSendingRate)
    }
}