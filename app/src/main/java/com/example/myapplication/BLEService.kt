package com.example.myapplication

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.example.myapplication.gatt.GattServiceHandler
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

class BLEService : Service() {
    private val tag = BLEService::class.java.simpleName
    private var handler: Handler? = null
    private val connectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    private val detectedDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    private val serviceSemaphore = Semaphore(1)

    private var isAdvertising = false
    private var isScanning = false
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private val gattServiceHandlers: MutableList<GattServiceHandler> = ArrayList()
    val hidPeripheral: HidPeripheral = KeyboardPeripheral()
    var deviceDetectedListener: DeviceDetectedListener? = null

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
        gattServiceHandlers.addAll(hidPeripheral.gattServiceHandlers)
        for (gattServiceHandler in gattServiceHandlers) {
            val gattService = gattServiceHandler.setup()
            if (gattService != null) {
                addService(gattService)
            }
        }
        startReportingNotifications(50)
    }

    private val devices: Set<BluetoothDevice>
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
            Log.i(tag, "Detected device ${result?.device?.address}")
            if (result != null) {
                synchronized(detectedDevicesMap) {
                    detectedDevicesMap.put(result.device.address, result.device)
                }
                deviceDetectedListener?.onDeviceDetected(result.device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
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
                    handler?.post {
                        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                        isAdvertising = false
                    }
                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.put(device.address, device)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val deviceAddress = device.address

                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.remove(deviceAddress)
                    }
                    startAdvertising()
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
        }
        Log.d(tag, "Service: ${service.uuid} added.")
    }

    fun startAdvertising() {
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

            // set up advertising data
            val advertiseDataBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)

            // set up scan result
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

    fun stopAdvertising() {
        if (!isAdvertising) {
            Log.d(tag, "No advertising, ignoring")
            return
        }
        handler?.post {
            try {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
            try {
                if (gattServer != null) {
                    val devices = devices
                    for (device in devices) {
                        gattServer!!.cancelConnection(device)
                    }
                    gattServer!!.close()
                    gattServer = null
                }
            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
        }
    }

    fun startScanning() {
        synchronized(detectedDevicesMap) {
            if (isScanning) {
                Log.w(tag, "Already scanning??")
                return
            }
            isScanning = true
        }
        Log.d(tag, "startScanning")
        handler?.post {
            bluetoothLeScanner?.startScan(scanCallback)
        }

    }

    fun stopScanning() {
        if (!isScanning) {
            return
        }
        handler?.post {
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

    override fun onBind(p0: Intent?): IBinder {
        return LocalBinder()
    }

    override fun onDestroy() {
        stopAdvertising()
    }
}
