package com.example.myapplication

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.StandardCharsets
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

class GattServerCallback(
    var applicationContext: Context,
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser,
    private val reportMap: ByteArray
) :
    BluetoothGattServerCallback() {
    private val TAG = GattServerCallback::class.java.simpleName
    private val handler = Handler(applicationContext.mainLooper)
    private val serviceSemaphore = Semaphore(1)
    private val bluetoothDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()
    private val EMPTY_BYTES = byteArrayOf()
    private var isAdvertising = false

    /**
     * Obtains connected Bluetooth devices
     *
     * @return the connected Bluetooth devices
     */
    private val devices: Set<BluetoothDevice>
        get() {
            val deviceSet: MutableSet<BluetoothDevice> = HashSet()
            synchronized(bluetoothDevicesMap) { deviceSet.addAll(bluetoothDevicesMap.values) }
            return Collections.unmodifiableSet(deviceSet)
        }
    var gattServer: BluetoothGattServer? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "Advertising started successfully :)")
            serviceSemaphore.release()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Advertising failure: $errorCode")
            serviceSemaphore.release()
        }
    }

    fun addService(service: BluetoothGattService) {
        var serviceAdded = false
        var tries = 0

        handler.post {
            if (serviceSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                while (!serviceAdded && tries < 3) {
                    serviceAdded = gattServer!!.addService(service)
                    tries += 1
                }
            } else {
                throw java.lang.IllegalStateException("Cannot obtain semaphore to add gatt service")
            }
        }
        Log.d(TAG, "Service: " + service.uuid + " added.")
    }

    /**
     * Starts advertising
     */
    fun startAdvertising() {
        if (isAdvertising) {
            Log.w(TAG, "Already advertising???")
            return
        }
        handler.post { // set up advertising setting
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

            bluetoothLeAdvertiser.startAdvertising(
                advertiseSettings,
                advertiseDataBuilder.build(),
                scanResultBuilder.build(),
                advertiseCallback
            )
        }
    }

    /**
     * Stops advertising
     */
    fun stopAdvertising() {
        handler.post {
            try {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
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

    fun startReportingNotifications(
        dataSendingRate: Long,
        inputReportQueue: Queue<ByteArray>,
        inputReportCharacteristic: BluetoothGattCharacteristic?
    ) {
        // send report each dataSendingRate, if data available
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val polled = inputReportQueue.poll()
                if (polled != null && inputReportCharacteristic != null) {
                    inputReportCharacteristic.value = polled
                    handler.post {
                        val devices = devices
                        if (devices.isEmpty()) {
                            Log.d(TAG, "No devices to send notification")
                        }
                        for (device in devices) {
                            try {
                                if (gattServer != null) {
                                    val result = gattServer!!.notifyCharacteristicChanged(
                                        device,
                                        inputReportCharacteristic,
                                        false
                                    )
                                    if (!result) {
                                        Log.e(TAG, "Failed to send notification. Reason unknown.")
                                    } else {
                                        Log.d(TAG, "Notification sent")
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error on sending notification ${e.localizedMessage}")
                            }
                        }
                    }
                }
            }
        }, 0, dataSendingRate)
    }

    override fun onConnectionStateChange(
        device: BluetoothDevice,
        status: Int,
        newState: Int
    ) {
        super.onConnectionStateChange(device, status, newState)
        Log.d(
            TAG,
            "onConnectionStateChange status: " + status + ", newState: " + connStateLabels[newState]
        )
        if (status != 0) {
            Log.e(TAG, "Error on connection state change: $status")
            return
        }
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.d(
                    TAG,
                    "BluetoothProfile.STATE_CONNECTED bondState: " + bondStateLabels[device.bondState]
                )
                handler.post {
                    bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
                    isAdvertising = false
                }
                synchronized(bluetoothDevicesMap) {
                    bluetoothDevicesMap.put(device.address, device)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                val deviceAddress = device.address

                synchronized(bluetoothDevicesMap) {
                    bluetoothDevicesMap.remove(deviceAddress)
                    startAdvertising()
                }
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
            TAG,
            "onCharacteristicReadRequest characteristic: " + characteristic.uuid + ", offset: " + offset
        )
        if (gattServer == null) {
            return
        }
        handler.post {
            val characteristicUuid = characteristic.uuid
            if (BleUuidUtils.matches(HIDService.CHARACTERISTIC_HID_INFORMATION, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    HIDService.CHARACTERISTIC_HID_INFORMATION_VALUE
                )
            } else if (BleUuidUtils.matches(HIDService.CHARACTERISTIC_REPORT_MAP, characteristicUuid)) {
                if (offset == 0) {
                    gattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        reportMap
                    )
                } else {
                    val remainLength = reportMap.size - offset
                    if (remainLength > 0) {
                        val data = ByteArray(remainLength)
                        System.arraycopy(reportMap, offset, data, 0, remainLength)
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            data
                        )
                    } else {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            null
                        )
                    }
                }
            } else if (BleUuidUtils.matches(HIDService.CHARACTERISTIC_HID_CONTROL_POINT, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    byteArrayOf(0)
                )
            } else if (BleUuidUtils.matches(HIDService.CHARACTERISTIC_REPORT, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    EMPTY_BYTES
                )
            } else if (BleUuidUtils.matches(DeviceInfoService.CHARACTERISTIC_MANUFACTURER_NAME, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    "Samsung".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            } else if (BleUuidUtils.matches(DeviceInfoService.CHARACTERISTIC_SERIAL_NUMBER, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    "12345".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            } else if (BleUuidUtils.matches(DeviceInfoService.CHARACTERISTIC_MODEL_NUMBER, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    "Klawiaturka".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            } else if (BleUuidUtils.matches(BatteryService.CHARACTERISTIC_BATTERY_LEVEL, characteristicUuid)) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    byteArrayOf(0x64)
                ) // always 100%
            } else if (BleUuidUtils.matches(DeviceInfoService.CHARACTERISTIC_PNP_ID, characteristicUuid)) {
                Log.w(TAG, "HANDLING PNP_ID characteristic value = ${DeviceInfoService.CHARACTERISTIC_PNP_ID_VALUE}")
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    DeviceInfoService.CHARACTERISTIC_PNP_ID_VALUE
                )
            } else {
                Log.w(
                    TAG,
                    "Responding with implicit characteristic value. UUID=${characteristicUuid} val=${characteristic.value}"
                )
                gattServer!!.sendResponse(
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
            TAG,
            "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid
        )
        if (gattServer == null) {
            return
        }
        handler.post {
            if (BleUuidUtils.matches(GattService.DESCRIPTOR_REPORT_REFERENCE, descriptor.uuid)) {
                val characteristicProperties = descriptor.characteristic.properties
                if (characteristicProperties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                    // Input Report
                    gattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 1)
                    )
                } else if (characteristicProperties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                    // Output Report
                    gattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 2)
                    )
                } else if (characteristicProperties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE) {
                    // Feature Report
                    gattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 3)
                    )
                } else {
                    gattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        EMPTY_BYTES
                    )
                }
            } else if (BleUuidUtils.matches(
                    GattService.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    descriptor.uuid
                )
            ) {
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                Log.e(
                    TAG,
                    "Unhandled descriptor " + descriptor.uuid + " responding with empty bytes :/"
                )
                gattServer!!.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    EMPTY_BYTES
                )
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
            TAG,
            "onCharacteristicWriteRequest characteristic: ${characteristic.uuid}, value: ${value.contentToString()}"
        )
        if (gattServer == null) {
            return
        }
        if (responseNeeded) {
            handler.post {
                if (BleUuidUtils.matches(HIDService.CHARACTERISTIC_REPORT, characteristic.uuid)) {
                    if (characteristic.properties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                        // Output Report
//                        onOutputReport(value)

                        // send empty
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            EMPTY_BYTES
                        )
                    } else {
                        // send empty
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            EMPTY_BYTES
                        )
                    }
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
            TAG,
            "onDescriptorWriteRequest descriptor: ${descriptor.uuid}, value: ${value.contentToString()}, responseNeeded: $responseNeeded, preparedWrite: $preparedWrite"
        )
        descriptor.value = value
        if (responseNeeded) {
            handler.post {
                if (BleUuidUtils.matches(
                        GattService.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                        descriptor.uuid
                    )
                ) {
                    // send empty
                    if (gattServer != null) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            value
                        )
                    }
                }
            }
        }
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        super.onServiceAdded(status, service)
        Log.d(TAG, "onServiceAdded status: " + status + ", service: " + service.uuid)
        if (status != 0) {
            throw IllegalStateException("Adding service with uuid ${service.uuid} failed")
        }
        serviceSemaphore.release()
    }
}
