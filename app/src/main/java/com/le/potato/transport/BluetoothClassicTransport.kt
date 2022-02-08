package com.le.potato.transport

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
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
    private var _isScanning = false
    private val detectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    var deviceDiscoveryListener: DeviceDiscoveryListener? = null
    private val inputReportQueue: Queue<Pair<Int, ByteArray>> = ConcurrentLinkedQueue()

    val isScanning: Boolean
        get() = _isScanning

    private fun finalizeConnection(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.put(device.address, device)
        }
        connectingDevice = null
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
        startReportingNotifications()
    }

    private val btReceiver = object : BroadcastReceiver() {
        //todo: handle turning bluetooth adapter off/on
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val bluetoothDevice =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice? ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {

                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, Integer.MIN_VALUE)
                    if (bondState == BluetoothDevice.BOND_BONDED && bluetoothDevice == connectingDevice) {
                        finalizeConnection(bluetoothDevice)
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    synchronized(detectedDevicesMap) {
                        detectedDevicesMap.put(bluetoothDevice.address, bluetoothDevice)
                    }
                    deviceDiscoveryListener?.onDeviceDetected(bluetoothDevice)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> deviceDiscoveryListener?.onDiscoveryStarted()
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> deviceDiscoveryListener?.onDiscoveryStopped()
                else -> {
                    Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
                }
            }
        }
    }

    inner class ClassicHidCallback(private var targetDevice: BluetoothDevice) : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.i(tag, "ClassicHID app status changed device is $pluggedDevice registered $registered")

            if (hidDevice?.connect(targetDevice) != true) {
                fireDeviceConnectionErrorEvent(targetDevice, "Zjebało się")
            }

        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (device == null) return
            Log.i(tag, "ClassicHID connection state changed state = $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    finalizeConnection(device)
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
            hidDevice?.replyReport(device, type, id, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
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

    override fun addInputReport(reportId: Int, report: ByteArray) {
        inputReportQueue.offer(Pair(reportId, report))
    }

    override fun disconnect(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.remove(device.address)
            if (hidDevice?.getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
                hidDevice?.disconnect(device)
            }
            fireDeviceDisconnectedEvent(device)
        }
    }

    override fun deactivate() {
        stopScanning()
        val hidDevice = hidDevice ?: return
        for (device in hidDevice.connectedDevices) {
            hidDevice.disconnect(device)
        }
        hidDevice.unregisterApp()
    }

    fun connectToDevice(device: BluetoothDevice?) {
        if (device == null) {
            return
        }
        synchronized(connectedDevicesMap) {
            if (connectedDevicesMap.containsKey(device.address)) {
                Log.d(tag, "Already connected. Skipping.")
                return
            }
        }
        stopScanning()
        connectingDevice = device
        fireDeviceConnectingEvent(device)
        synchronized(detectedDevicesMap) {
            if (detectedDevicesMap.isNotEmpty()) {
                hidDevice?.unregisterApp()
            }
        }
        // todo: this is dirty as Sasha Gray
        handler.postDelayed({
            setupHIDProfile(device)
        }, 5000)
    }

    private fun setupHIDProfile(device: BluetoothDevice) {
//        hidDevice?.unregisterApp()
//        hidDevice = null
        Log.d(tag, "Setting up HID profile")
        bluetoothAdapter.getProfileProxy(applicationContext, object : BluetoothProfile.ServiceListener {

            override fun onServiceConnected(status: Int, btProfile: BluetoothProfile?) {
                val hidProfile: BluetoothHidDevice = btProfile as BluetoothHidDevice
                hidDevice = hidProfile
                Log.i(tag, "Profile HID connected")
                val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                    "KogucikHID",
                    "OMG",
                    "Przemo",
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
                hidProfile.registerApp(
                    sdpSettings,
                    qusInSettings,
                    qosOutSettings,
                    applicationContext.mainExecutor,
                    ClassicHidCallback(device)
                )
            }

            override fun onServiceDisconnected(p0: Int) {
                Log.e("KURWA", "Status = $p0")
                hidDevice = null
            }

        }, BluetoothProfile.HID_DEVICE)
    }

    fun startScanning() {
        synchronized(detectedDevicesMap) {
            if (isScanning) {
                Log.d(tag, "Already scanning, skipping")
                return
            }
            _isScanning = true
        }
        handler.postDelayed({
            Log.i(tag, "Stopping discovery after 20s.")
            stopScanning()
        }, 20000)
        handler.post {
            Log.d(tag, "startScanning")
            if (!bluetoothAdapter.startDiscovery()) {
                Log.wtf(tag, "Start discovery failed!")
            }
        }
    }

    fun stopScanning() {
        if (!isScanning) {
            return
        }
        handler.post {
            Log.d(tag, "stopScanning")
            if (!bluetoothAdapter.cancelDiscovery()) {
                Log.wtf(tag, "Cancel discovery failed :(")
            }
        }
        _isScanning = false
    }

    private fun startReportingNotifications(dataSendingRate: Long = 20) {
        // send report each dataSendingRate, if data available
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {

                val polled = inputReportQueue.poll()
                if (polled != null) {
                    handler.post {
                        val devices = devices
                        if (devices.isEmpty()) {
                            Log.d(tag, "No devices to send notification")
                        }
                        for (device in devices) {
                            try {
                                val result = hidDevice?.sendReport(device, polled.first, polled.second)
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