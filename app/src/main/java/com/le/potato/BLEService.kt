package com.le.potato

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import com.le.potato.gatt.GattServiceHandler
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


val connStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
    init {
        put(BluetoothProfile.STATE_CONNECTED, "STATE_CONNECTED")
        put(BluetoothProfile.STATE_DISCONNECTED, "STATE_DISCONNECTED")
        put(BluetoothProfile.STATE_CONNECTING, "STATE_CONNECTING")
        put(BluetoothProfile.STATE_DISCONNECTING, "STATE_DISCONNECTING")
    }
}
val bondStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
    init {
        put(BluetoothDevice.BOND_NONE, "BOND_NONE")
        put(BluetoothDevice.BOND_BONDING, "BOND_BONDING")
        put(BluetoothDevice.BOND_BONDED, "BOND_BONDED")
    }
}

interface DeviceDetectedListener {
    fun onDeviceDetected(device: BluetoothDevice)
}

interface DeviceConnectedListener {
    fun onDeviceConnected(device: BluetoothDevice)
    fun onDeviceConnecting(device: BluetoothDevice)
    fun onDeviceDisconnected(device: BluetoothDevice)
}

class BLEService : Service() {
    private val tag = BLEService::class.java.simpleName
    private var handler: Handler? = null
    private val connectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    private val detectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    private val serviceSemaphore = Semaphore(1)

    private var isAdvertising = false
    private var isScanning = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private val gattServiceHandlers: MutableList<GattServiceHandler> = ArrayList()

    // todo: some sort of DI to inject gattService handlers and move keyboard out of here
    val keyboard = KeyboardWithPointer()
    var deviceDetectedListener: DeviceDetectedListener? = null
    var deviceConnectedListener: DeviceConnectedListener? = null

    override fun onCreate() {
        handler = Handler(applicationContext.mainLooper)
        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
            ?: throw UnsupportedOperationException("Bluetooth is not available.")
        while (!bluetoothAdapter.isEnabled) {
            Log.d(tag, "Waiting for bluetooth adapter to be enabled")
            Thread.sleep(1000)
        }
        this.bluetoothAdapter = bluetoothAdapter
        Log.d(
            tag,
            "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported
        )
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            ?: throw UnsupportedOperationException("Bluetooth LE Scanning is not supported on this device.")
        gattServer = bluetoothManager.openGattServer(applicationContext, GattServerCallback())
            ?: throw UnsupportedOperationException("gattServer is null, check Bluetooth is ON.")

        gattServiceHandlers.addAll(keyboard.gattServiceHandlers)
        for (gattServiceHandler in gattServiceHandlers) {
            val gattService = gattServiceHandler.setup()
            if (gattService != null) {
                addService(gattService)
            }
        }
        startReportingNotifications(50)
        startAdvertising()
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
//        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
//        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
//        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
//        intentFilter.addAction(BluetoothDevice.ACTION_UUID)
//        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
//        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(btReceiver, intentFilter)
    }

    private val btReceiver = object : BroadcastReceiver() {
        //todo: handle turning bluetooth adapter off/on
        override fun onReceive(context: Context?, intent: Intent?) {
            val bluetoothDevice =
                intent!!.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, Integer.MIN_VALUE)
                    if (bondState == BluetoothDevice.BOND_BONDED && bluetoothDevice != null) {
                        finalizeConnection(bluetoothDevice)
                    }
                }
                else -> {
                    Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
                }
            }
        }

    }

    val devices: Set<BluetoothDevice>
        get() {
            val deviceSet: MutableSet<BluetoothDevice> = HashSet()
            synchronized(connectedDevicesMap) { deviceSet.addAll(connectedDevicesMap.values) }
            return Collections.unmodifiableSet(deviceSet)
        }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(tag, "Advertising started successfully :)")
            serviceSemaphore.release()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(tag, "Advertising failure: $errorCode")
            serviceSemaphore.release()
        }
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

    inner class GattServerCallback : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(
                tag,
                "onConnectionStateChange status: " + status + ", newState: " + connStateLabels[newState]
            )
            if (status != 0) {
                Log.e(tag, "Error on connection state change: $status")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(
                        tag,
                        "BluetoothProfile.STATE_CONNECTED bondState: " + bondStateLabels[device.bondState]
                    )
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        finalizeConnection(device)
                    } else {
                        deviceConnectedListener?.onDeviceConnecting(device)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val deviceAddress = device.address

                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.remove(deviceAddress)
                    }
                    deviceConnectedListener?.onDeviceDisconnected(device)
                    startAdvertising()
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    deviceConnectedListener?.onDeviceConnecting(device)
                }
                else -> {}
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(
                tag,
                "onCharacteristicReadRequest characteristic: ${characteristic.uuid}, offset: $offset"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                var handled = false
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onCharacteristicReadRequest(
                            device,
                            requestId,
                            offset,
                            characteristic,
                            gattServer
                        )
                    ) {
                        handled = true
                        break
                    }
                }
                if (!handled) {
                    Log.w(
                        tag,
                        "Responding with implicit characteristic value. UUID=${characteristic.uuid} val=${characteristic.value}"
                    )
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        characteristic.value
                    )
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.d(
                tag,
                "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onDescriptorReadRequest(device, requestId, offset, descriptor, gattServer)) {
                        break
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Log.d(
                tag,
                "onCharacteristicWriteRequest characteristic: ${characteristic.uuid}, value: ${value.contentToString()}"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onCharacteristicWriteRequest(
                            device,
                            requestId,
                            characteristic,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value,
                            gattServer
                        )
                    ) {
                        break
                    }
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Log.d(
                tag,
                "onDescriptorWriteRequest descriptor: ${descriptor.uuid}, value: ${value.contentToString()}, responseNeeded: $responseNeeded, preparedWrite: $preparedWrite"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onDescriptorWriteRequest(
                            device,
                            requestId,
                            descriptor,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value,
                            gattServer
                        )
                    ) {
                        break
                    }
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.d(tag, "onServiceAdded status: " + status + ", service: " + service.uuid)
            if (status != 0) {
                throw IllegalStateException("Adding service with uuid ${service.uuid} failed")
            }
            serviceSemaphore.release()
        }
    }

    private fun finalizeConnection(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.put(device.address, device)
        }
        stopAdvertising()
        deviceConnectedListener?.onDeviceConnected(device)
    }

    private fun addService(service: BluetoothGattService) {
        var serviceAdded = false
        var tries = 0

        handler?.post {
            if (serviceSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                while (!serviceAdded && tries < 3) {
                    serviceAdded = gattServer!!.addService(service)
                    tries += 1
                }
            } else {
                throw java.lang.IllegalStateException("Cannot obtain semaphore to add gatt service")
            }
            if (!serviceAdded) {
                throw java.lang.IllegalStateException("Cannot add gatt service ${service.uuid}")
            } else {
                Log.d(tag, "Service: ${service.uuid} added.")
            }
        }
    }

    private fun startAdvertising() {
        synchronized(connectedDevicesMap) {
            if (isAdvertising || connectedDevicesMap.isNotEmpty()) {
                Log.w(tag, "Already advertising???")
                return
            }
        }

        handler?.post { // set up advertising setting
            if (!serviceSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                throw java.lang.IllegalStateException("Cannot obtain semaphore to start advertising")
            }
            isAdvertising = true
            val advertiseSettings = AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .build()
            val serviceUUIDs = gattServer?.services?.map {
                ParcelUuid.fromString(it.uuid.toString())
            } ?: throw java.lang.IllegalStateException("No services present in gatt server")

            val advertiseDataBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)

            // todo: payload is limited to 31 bytes if device name is too long this will explode with power of 1000 suns
            //  we may want to truncate device name somehow or prompt the user
            val scanResultBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)

            for (serviceUuid in serviceUUIDs) {
                advertiseDataBuilder.addServiceUuid(serviceUuid)
                scanResultBuilder.addServiceUuid(serviceUuid)
            }

            bluetoothLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseDataBuilder.build(),
                scanResultBuilder.build(),
                advertiseCallback
            )
        }
    }

    private fun stopAdvertising() {
        if (!isAdvertising) {
            Log.d(tag, "No advertising, ignoring")
            return
        }
        handler?.post {
            try {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
        }
    }

    private fun closeGattServer() {
        val gattServer = gattServer ?: return
        handler?.post {
            try {
                for (device in devices) {
                    gattServer.cancelConnection(device)
                }
                gattServer.close()
                this.gattServer = null

            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
        }
    }

    fun startScanning() {
        synchronized(detectedDevicesMap) {
            if (isScanning) {
                Log.d(tag, "Already scanning, skipping")
                return
            }
            isScanning = true
        }
        handler?.postDelayed({
            Log.i(tag, "Stopping discovery after 10s.")
            stopScanning()
        }, 10000)
        handler?.post {
            Log.d(tag, "startScanning")
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
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
        handler?.post {
            Log.d(tag, "stopScanning")
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
    }

    private fun startReportingNotifications(dataSendingRate: Long) {
        // send report each dataSendingRate, if data available
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val gattServer = gattServer ?: return
                for (gattHandler in gattServiceHandlers) {
                    val (inputReportCharacteristic, polled) = gattHandler.pollInputReportQueue()
                    if (polled != null && inputReportCharacteristic != null) {
                        //todo: wait for notificationSent callback in gattServerCallback
                        handler?.post {
                            val devices = devices
                            if (devices.isEmpty()) {
                                Log.d(tag, "No devices to send notification")
                            }
                            inputReportCharacteristic.value = polled
                            for (device in devices) {
                                try {
                                    val result = gattServer.notifyCharacteristicChanged(
                                        device,
                                        inputReportCharacteristic,
                                        false
                                    )
                                    if (!result) {
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
            }
        }, 0, dataSendingRate)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BLEService {
            return this@BLEService
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
        handler?.post {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            // Todo: this (usually) doesn't work.
            //  Peripheral devices cannot initiate connection. We should rather try reconnection with role switch.
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                Log.i(tag, "Trying to bond with the device")
                device.createBond()
            }
            gattServer?.connect(device, false)

        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return LocalBinder()
    }

    override fun onDestroy() {
        stopAdvertising()
        stopScanning()
        unregisterReceiver(btReceiver)
        closeGattServer()

    }

}
