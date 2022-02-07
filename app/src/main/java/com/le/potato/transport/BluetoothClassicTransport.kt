package com.le.potato.transport

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.le.potato.KeyboardWithPointer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

interface DeviceDetectedListener {
    fun onDeviceDetected(device: BluetoothDevice)
}

class BluetoothClassicTransport : AbstractHIDTransport() {
    private val tag = BluetoothClassicTransport::class.java.simpleName
    private var hidDevice: BluetoothHidDevice? = null
    private var _isScanning = false
    private val detectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    var deviceDetectedListener: DeviceDetectedListener? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val inputReportQueue: Queue<Pair<Int, ByteArray>> = ConcurrentLinkedQueue()

    val isScanning: Boolean
        get() = _isScanning

    private fun finalizeConnection(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.put(device.address, device)
        }
        fireDeviceConnectedEvent(device)
    }

    override fun init(context: Context, reportMap: ByteArray) {
        super.init(context, reportMap)
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            ?: throw UnsupportedOperationException("Bluetooth LE Scanning is not supported on this device.")
        startReportingNotifications(20)
    }

    val classicHidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.i(tag, "ClassicHID app status changed device is $pluggedDevice registered $registered")
            if (pluggedDevice != null && registered) {
                hidDevice?.connect(pluggedDevice)
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
        hidDevice.unregisterApp()
        for (device in hidDevice.connectedDevices) {
            hidDevice.disconnect(device)
        }
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
        fireDeviceConnectingEvent(device)
        handler.post {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                Log.i(tag, "Trying to bond with the device")
                device.createBond()
            } else {
                Log.i(tag, "Trying to connect classic hid")
                setupHIDProfile(device)
            }
        }
    }

    private fun setupHIDProfile(device: BluetoothDevice) {
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
                    KeyboardWithPointer.reportMap
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
                    classicHidCallback
                )
            }

            override fun onServiceDisconnected(p0: Int) {
                Log.e("KURWA", "Status = $p0")
                hidDevice = null
            }

        }, BluetoothProfile.HID_DEVICE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result != null) {
                synchronized(detectedDevicesMap) {
                    detectedDevicesMap.put(result.device.address, result.device)
                }
                deviceDetectedListener?.onDeviceDetected(result.device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.i(tag, "onBatchScanResults")
            if (results != null) {
                for (result in results) {
                    synchronized(detectedDevicesMap) {
                        detectedDevicesMap[result.device.address] = result.device
                    }
                    deviceDetectedListener?.onDeviceDetected(result.device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(tag, "BLE Scan failed, errorcode = $errorCode")
        }
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
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                .setReportDelay(0)
                .build()
            bluetoothLeScanner?.startScan(arrayListOf(ScanFilter.Builder().build()), scanSettings, scanCallback)
        }
    }

    fun stopScanning() {
        if (!isScanning) {
            return
        }
        handler.post {
            Log.d(tag, "stopScanning")
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        _isScanning = false
    }

    private fun startReportingNotifications(dataSendingRate: Long) {
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