package com.example.myapplication

import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattServerCallback
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.util.Log
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.UnsupportedOperationException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Constructor<br></br>
 * Before constructing the instance, check the Bluetooth availability.
 *
 * @param context the ApplicationContext
 * @param needInputReport true: serves 'Input Report' BLE characteristic
 * @param needOutputReport true: serves 'Output Report' BLE characteristic
 * @param needFeatureReport true: serves 'Feature Report' BLE characteristic
 * @param dataSendingRate sending rate in milliseconds
 * @throws UnsupportedOperationException if starting Bluetooth LE Peripheral failed
 */

abstract class HidPeripheral protected constructor(
    context: Context,
    needInputReport: Boolean,
    needOutputReport: Boolean,
    needFeatureReport: Boolean,
    dataSendingRate: Int
) {
    private var manufacturer = "ACME LTD"
    private var deviceName = "BLERC HID"
    private var serialNumber = "12345678"

    /**
     * Represents Report Map byte array
     * @return Report Map data
     */
    protected abstract val reportMap: ByteArray

    /**
     * HID Input Report
     */
    private val inputReportQueue: Queue<ByteArray> = ConcurrentLinkedQueue()
    protected fun addInputReport(inputReport: ByteArray?) {
        if (inputReport != null && inputReport.isNotEmpty()) {
            inputReportQueue.offer(inputReport)
        }
    }

    /**
     * HID Output Report
     *
     * @param outputReport the report data
     */
    protected abstract fun onOutputReport(outputReport: ByteArray?)

    /**
     * Instances for the peripheral
     */
    private var applicationContext: Context? = null
    private var handler: Handler? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null
    private var gattServer: BluetoothGattServer? = null
    private val bluetoothDevicesMap: MutableMap<String, BluetoothDevice> = HashMap()

    /**
     * Add GATT service to gattServer
     */
    private fun addNextService() {
        assert(gattServer != null)
        var serviceAdded = false
        var tries = 0
        val service = servicesToAdd.poll() ?: return

        while (!serviceAdded && tries < 3) {
            serviceAdded = gattServer!!.addService(service)
            tries += 1
        }
        Log.d(TAG, "Service: " + service.uuid + " added.")
    }

    /**
     * Setup HID Service
     *
     * @param isNeedInputReport true: serves 'Input Report' BLE characteristic
     * @param isNeedOutputReport true: serves 'Output Report' BLE characteristic
     * @param isNeedFeatureReport true: serves 'Feature Report' BLE characteristic
     * @return the service
     */
    private fun setUpHidService(
        isNeedInputReport: Boolean,
        isNeedOutputReport: Boolean,
        isNeedFeatureReport: Boolean
    ): BluetoothGattService {
        val service =
            BluetoothGattService(SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_INFORMATION,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            while (!service.addCharacteristic(characteristic));
        }

        // Report Map
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            while (!service.addCharacteristic(characteristic));
        }

        // Protocol Mode
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            while (!service.addCharacteristic(characteristic));
        }

        // HID Control Point
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            while (!service.addCharacteristic(characteristic));
        }

        // Input Report
        if (isNeedInputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            ) //  | BluetoothGattDescriptor.PERMISSION_WRITE
            clientCharacteristicConfigurationDescriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)
            val reportReferenceDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(reportReferenceDescriptor)
            while (!service.addCharacteristic(characteristic));
            inputReportCharacteristic = characteristic
        }

        // Output Report
        if (isNeedOutputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            while (!service.addCharacteristic(characteristic));
        }

        // Feature Report
        if (isNeedFeatureReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            while (!service.addCharacteristic(characteristic));
        }
        return service
    }

    /**
     * Starts advertising
     */
    private fun startAdvertising() {
        handler?.post { // set up advertising setting
            val advertiseSettings = AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .build()

            // set up advertising data
            val advertiseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
                .build()

            // set up scan result
//                final AdvertiseData scanResult = new Builder()
//                        .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
//                        .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
//                        .addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
//                        .build();

//                Log.d(TAG, "advertiseData: " + advertiseData + ", scanResult: " + scanResult);
            bluetoothLeAdvertiser!!.startAdvertising(
                advertiseSettings,
                advertiseData,
                advertiseCallback
            )
        }
    }

    /**
     * Stops advertising
     */
    fun stopAdvertising() {
        handler?.post {
            try {
                bluetoothLeAdvertiser!!.stopAdvertising(advertiseCallback)
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

    /**
     * Callback for BLE connection<br></br>
     * nothing to do.
     */
    private val advertiseCallback = object: AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "Advertising started successfully :)")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Advertising failure: $errorCode")
        }
    }

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
    private val connStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
        init {
            put(BluetoothProfile.STATE_CONNECTED, "STATE_CONNECTED")
            put(BluetoothProfile.STATE_DISCONNECTED, "STATE_DISCONNECTED")
            put(BluetoothProfile.STATE_CONNECTING, "STATE_CONNECTING")
            put(BluetoothProfile.STATE_DISCONNECTING, "STATE_DISCONNECTING")
        }
    }
    private val bondStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
        init {
            put(BluetoothDevice.BOND_NONE, "BOND_NONE")
            put(BluetoothDevice.BOND_BONDING, "BOND_BONDING")
            put(BluetoothDevice.BOND_BONDED, "BOND_BONDED")
        }
    }

    /**
     * Callback for BLE data transfer
     */
    private val gattServerCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
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
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        // check bond status
                        Log.d(
                            TAG,
                            "BluetoothProfile.STATE_CONNECTED bondState: " + bondStateLabels[device.bondState]
                        )
                        if (device.bondState == BluetoothDevice.BOND_NONE) {
                            applicationContext?.registerReceiver(object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    val action = intent.action
                                    Log.d(TAG, "onReceive action: $action")
                                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                                        val state = intent.getIntExtra(
                                            BluetoothDevice.EXTRA_BOND_STATE,
                                            BluetoothDevice.ERROR
                                        )
                                        if (state == BluetoothDevice.BOND_BONDED) {
                                            val bondedDevice =
                                                intent.getParcelableExtra<BluetoothDevice>(
                                                    BluetoothDevice.EXTRA_DEVICE
                                                )

                                            // successfully bonded
                                            context.unregisterReceiver(this)
                                            handler?.post {
                                                if (gattServer != null) {
                                                    gattServer!!.connect(device, false)
                                                }
                                            }
                                            Log.d(TAG, "successfully bonded")
                                        } else {
                                            Log.e(
                                                TAG,
                                                String.format("unexpected bonding state: %d", state)
                                            )
                                        }
                                    }
                                }
                            }, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
                            device.createBond()
                        } else if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            handler?.post {
                                if (gattServer != null) {
                                    gattServer!!.connect(device, false)
                                }
                            }
                            synchronized(bluetoothDevicesMap) {
                                bluetoothDevicesMap.put(
                                    device.address,
                                    device
                                )
                            }
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val deviceAddress = device.address

                        // try reconnect immediately
                        handler?.post {
                            if (gattServer != null) {
                                // gattServer.cancelConnection(device);
                                gattServer!!.connect(device, false)
                            }
                        }
                        synchronized(bluetoothDevicesMap) { bluetoothDevicesMap.remove(deviceAddress) }
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
                handler?.post {
                    val characteristicUuid = characteristic.uuid
                    if (BleUuidUtils.matches(CHARACTERISTIC_HID_INFORMATION, characteristicUuid)) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            RESPONSE_HID_INFORMATION
                        )
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_REPORT_MAP,
                            characteristicUuid
                        )
                    ) {
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
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_HID_CONTROL_POINT,
                            characteristicUuid
                        )
                    ) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            byteArrayOf(0)
                        )
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristicUuid)) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            EMPTY_BYTES
                        )
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_MANUFACTURER_NAME,
                            characteristicUuid
                        )
                    ) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            manufacturer.toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_SERIAL_NUMBER,
                            characteristicUuid
                        )
                    ) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            serialNumber.toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_MODEL_NUMBER,
                            characteristicUuid
                        )
                    ) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            deviceName.toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                    } else if (BleUuidUtils.matches(
                            CHARACTERISTIC_BATTERY_LEVEL,
                            characteristicUuid
                        )
                    ) {
                        gattServer!!.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            byteArrayOf(0x64)
                        ) // always 100%
                    } else {
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
                handler?.post {
                    if (BleUuidUtils.matches(DESCRIPTOR_REPORT_REFERENCE, descriptor.uuid)) {
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
                            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
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
                    "onCharacteristicWriteRequest characteristic: " + characteristic.uuid + ", value: " + Arrays.toString(
                        value
                    )
                )
                if (gattServer == null) {
                    return
                }
                if (responseNeeded) {
                    if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristic.uuid)) {
                        if (characteristic.properties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                            // Output Report
                            onOutputReport(value)

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
                    "onDescriptorWriteRequest descriptor: " + descriptor.uuid + ", value: " + Arrays.toString(
                        value
                    ) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite
                )
                descriptor.value = value
                if (responseNeeded) {
                    if (BleUuidUtils.matches(
                            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
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
                                EMPTY_BYTES
                            )
                        }
                    }
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                super.onServiceAdded(status, service)
                Log.d(TAG, "onServiceAdded status: " + status + ", service: " + service.uuid)
                if (status != 0) {
                    throw IllegalStateException("Adding service with uuid ${service.uuid} failed")
                } else if (servicesToAdd.isNotEmpty()) {
                    addNextService()
                } else {
                    startAdvertising()
                }
            }
        }

    /**
     * Set the manufacturer name
     *
     * @param newManufacturer the name
     */
    fun setManufacturer(newManufacturer: String) {
        // length check
        val manufacturerBytes = newManufacturer.toByteArray(StandardCharsets.UTF_8)
        manufacturer = if (manufacturerBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(
                manufacturerBytes,
                0,
                bytes,
                0,
                DEVICE_INFO_MAX_LENGTH
            )
            String(bytes, StandardCharsets.UTF_8)
        } else {
            newManufacturer
        }
    }

    /**
     * Set the device name
     *
     * @param newDeviceName the name
     */
    fun setDeviceName(newDeviceName: String) {
        // length check
        val deviceNameBytes = newDeviceName.toByteArray(StandardCharsets.UTF_8)
        deviceName = if (deviceNameBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(
                deviceNameBytes,
                0,
                bytes,
                0,
                DEVICE_INFO_MAX_LENGTH
            )
            String(bytes, StandardCharsets.UTF_8)
        } else {
            newDeviceName
        }
    }

    /**
     * Set the serial number
     *
     * @param newSerialNumber the number
     */
    fun setSerialNumber(newSerialNumber: String) {
        // length check
        val deviceNameBytes = newSerialNumber.toByteArray(StandardCharsets.UTF_8)
        serialNumber = if (deviceNameBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(
                deviceNameBytes,
                0,
                bytes,
                0,
                DEVICE_INFO_MAX_LENGTH
            )
            String(bytes, StandardCharsets.UTF_8)
        } else {
            newSerialNumber
        }
    }

    companion object {
        private val TAG = HidPeripheral::class.java.simpleName

        /**
         * Main items
         */
        @JvmStatic
        protected fun INPUT(size: Int): Byte {
            return (0x80 or size).toByte()
        }

        @JvmStatic
        protected fun OUTPUT(size: Int): Byte {
            return (0x90 or size).toByte()
        }

        @JvmStatic
        protected fun COLLECTION(size: Int): Byte {
            return (0xA0 or size).toByte()
        }

        protected fun FEATURE(size: Int): Byte {
            return (0xB0 or size).toByte()
        }

        @JvmStatic
        protected fun END_COLLECTION(size: Int): Byte {
            return (0xC0 or size).toByte()
        }

        /**
         * Global items
         */
        @JvmStatic
        protected fun USAGE_PAGE(size: Int): Byte {
            return (0x04 or size).toByte()
        }

        @JvmStatic
        protected fun LOGICAL_MINIMUM(size: Int): Byte {
            return (0x14 or size).toByte()
        }

        @JvmStatic
        protected fun LOGICAL_MAXIMUM(size: Int): Byte {
            return (0x24 or size).toByte()
        }

        protected fun PHYSICAL_MINIMUM(size: Int): Byte {
            return (0x34 or size).toByte()
        }

        protected fun PHYSICAL_MAXIMUM(size: Int): Byte {
            return (0x44 or size).toByte()
        }

        protected fun UNIT_EXPONENT(size: Int): Byte {
            return (0x54 or size).toByte()
        }

        protected fun UNIT(size: Int): Byte {
            return (0x64 or size).toByte()
        }

        @JvmStatic
        protected fun REPORT_SIZE(size: Int): Byte {
            return (0x74 or size).toByte()
        }

        protected fun REPORT_ID(size: Int): Byte {
            return (0x84 or size).toByte()
        }

        @JvmStatic
        protected fun REPORT_COUNT(size: Int): Byte {
            return (0x94 or size).toByte()
        }

        /**
         * Local items
         */
        @JvmStatic
        protected fun USAGE(size: Int): Byte {
            return (0x08 or size).toByte()
        }

        @JvmStatic
        protected fun USAGE_MINIMUM(size: Int): Byte {
            return (0x18 or size).toByte()
        }

        @JvmStatic
        protected fun USAGE_MAXIMUM(size: Int): Byte {
            return (0x28 or size).toByte()
        }

        protected fun LSB(value: Int): Byte {
            return (value and 0xff).toByte()
        }

        protected fun MSB(value: Int): Byte {
            return (value shr 8 and 0xff).toByte()
        }

        /**
         * Device Information Service
         */
        private val SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A)
        private val CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29)
        private val CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24)
        private val CHARACTERISTIC_SERIAL_NUMBER = BleUuidUtils.fromShortValue(0x2A25)
        private val PNP_ID = BleUuidUtils.fromShortValue(0x2A50)
        private const val DEVICE_INFO_MAX_LENGTH = 20

        /**
         * Battery Service
         */
        private val SERVICE_BATTERY = BleUuidUtils.fromShortValue(0x180F)
        private val CHARACTERISTIC_BATTERY_LEVEL = BleUuidUtils.fromShortValue(0x2A19)

        /**
         * HID Service
         */
        private val SERVICE_BLE_HID = BleUuidUtils.fromShortValue(0x1812)
        private val CHARACTERISTIC_HID_INFORMATION = BleUuidUtils.fromShortValue(0x2A4A)
        private val CHARACTERISTIC_REPORT_MAP = BleUuidUtils.fromShortValue(0x2A4B)
        private val CHARACTERISTIC_HID_CONTROL_POINT = BleUuidUtils.fromShortValue(0x2A4C)
        private val CHARACTERISTIC_REPORT = BleUuidUtils.fromShortValue(0x2A4D)
        private val CHARACTERISTIC_PROTOCOL_MODE = BleUuidUtils.fromShortValue(0x2A4E)

        /**
         * Gatt Characteristic Descriptor
         */
        private val DESCRIPTOR_REPORT_REFERENCE = BleUuidUtils.fromShortValue(0x2908)
        private val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION =
            BleUuidUtils.fromShortValue(0x2902)
        private val EMPTY_BYTES = byteArrayOf()
        private val RESPONSE_HID_INFORMATION = byteArrayOf(0x11, 0x01, 0x00, 0x03)

        /**
         * Setup Device Information Service
         *
         * @return the service
         */
        private fun setUpDeviceInformationService(): BluetoothGattService {
            val service = BluetoothGattService(
                SERVICE_DEVICE_INFORMATION,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            run {
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_MANUFACTURER_NAME,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_MODEL_NUMBER,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_SERIAL_NUMBER,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    PNP_ID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                )
                characteristic.value = "\u0002\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray()
                while (!service.addCharacteristic(characteristic));
            }
            return service
        }

        /**
         * Setup Battery Service
         *
         * @return the service
         */
        private fun setUpBatteryService(): BluetoothGattService {
            val service =
                BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Battery Level
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            clientCharacteristicConfigurationDescriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)
            while (!service.addCharacteristic(characteristic));
            return service
        }
    }

    private val servicesToAdd: Queue<BluetoothGattService> = ConcurrentLinkedQueue()

    init {
        applicationContext = context.applicationContext
        handler = Handler(applicationContext!!.mainLooper)
        val bluetoothManager =
            applicationContext!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
            ?: throw UnsupportedOperationException("Bluetooth is not available.")
        if (!bluetoothAdapter.isEnabled) {
            throw UnsupportedOperationException("Bluetooth is disabled.")
        }
        Log.d(
            TAG,
            "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported
        )
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        Log.d(TAG, "bluetoothLeAdvertiser: $bluetoothLeAdvertiser")
        if (bluetoothLeAdvertiser == null) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }
        gattServer = bluetoothManager.openGattServer(applicationContext, gattServerCallback)
        if (gattServer == null) {
            throw UnsupportedOperationException("gattServer is null, check Bluetooth is ON.")
        }
        gattServer!!.clearServices()
        // setup services, they have to be added in a synchronous manner
        // (see https://developer.android.com/reference/android/bluetooth/BluetoothGattServer?hl=en#addService(android.bluetooth.BluetoothGattService))
        servicesToAdd.add(setUpDeviceInformationService())
        servicesToAdd.add(setUpHidService(needInputReport, needOutputReport, needFeatureReport))
        servicesToAdd.add(setUpBatteryService())

        addNextService()

        // send report each dataSendingRate, if data available
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val polled = inputReportQueue.poll()
                if (polled != null && inputReportCharacteristic != null) {
                    inputReportCharacteristic!!.value = polled
                    handler?.post {
                        val devices = devices
                        for (device in devices) {
                            try {
                                if (gattServer != null) {
                                    gattServer!!.notifyCharacteristicChanged(
                                        device,
                                        inputReportCharacteristic,
                                        false
                                    )
                                }
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                }
            }
        }, 0, dataSendingRate.toLong())
    }
}