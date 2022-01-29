package com.example.myapplication

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
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
    dataSendingRate: Long
) {
    private var manufacturer = "ACME LTD"
    private var deviceName = "BLERC HID"
    private var serialNumber = "12345678"

    /**
     * Represents Report Map byte array
     * @return Report Map data
     */
    abstract val reportMap: ByteArray
    /**
     * HID Input Report
     */
    private val inputReportQueue: Queue<ByteArray> = ConcurrentLinkedQueue()
    protected fun addInputReport(inputReport: ByteArray?) {
        if (inputReport != null && inputReport.isNotEmpty()) {
            inputReportQueue.offer(inputReport)
        }
    }
    private var serverCallback: GattServerCallback? = null

    /**
     * HID Output Report
     *
     * @param outputReport the report data
     */
    protected abstract fun onOutputReport(outputReport: ByteArray?)

    /**
     * Instances for the peripheral
     */
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null

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
            BluetoothGattService(HIDService.SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information
        run {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_HID_INFORMATION,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            while (!service.addCharacteristic(characteristic));
        }

        // Report Map
        run {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            while (!service.addCharacteristic(characteristic));
        }

        // Protocol Mode
        run {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            while (!service.addCharacteristic(characteristic));
        }

        // HID Control Point
        run {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            while (!service.addCharacteristic(characteristic));
        }

        // Input Report
        if (isNeedInputReport) {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                GattService.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            ) //  | BluetoothGattDescriptor.PERMISSION_WRITE
            clientCharacteristicConfigurationDescriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)
            val reportReferenceDescriptor = BluetoothGattDescriptor(
                GattService.DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(reportReferenceDescriptor)
            while (!service.addCharacteristic(characteristic));
            inputReportCharacteristic = characteristic
        }

        // Output Report
        if (isNeedOutputReport) {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val descriptor = BluetoothGattDescriptor(
                GattService.DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            while (!service.addCharacteristic(characteristic));
        }

        // Feature Report
        if (isNeedFeatureReport) {
            val characteristic = BluetoothGattCharacteristic(
                HIDService.CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            val descriptor = BluetoothGattDescriptor(
                GattService.DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            while (!service.addCharacteristic(characteristic));
        }
        return service
    }



    /**
     * Set the manufacturer name
     *
     * @param newManufacturer the name
     */
    private fun _setManufacturer(newManufacturer: String) {
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
        protected fun INPUT(size: Int = 1): Byte {
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


        private const val DEVICE_INFO_MAX_LENGTH = 20

        /**
         * Setup Device Information Service
         *
         * @return the service
         */
        private fun setUpDeviceInformationService(): BluetoothGattService {
            val service = BluetoothGattService(
                DeviceInfoService.SERVICE_DEVICE_INFORMATION,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            run {
                val characteristic = BluetoothGattCharacteristic(
                    DeviceInfoService.CHARACTERISTIC_MANUFACTURER_NAME,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    DeviceInfoService.CHARACTERISTIC_MODEL_NUMBER,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    DeviceInfoService.CHARACTERISTIC_SERIAL_NUMBER,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                while (!service.addCharacteristic(characteristic));
            }
            run {
                val characteristic = BluetoothGattCharacteristic(
                    DeviceInfoService.CHARACTERISTIC_PNP_ID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                characteristic.value = DeviceInfoService.CHARACTERISTIC_PNP_ID_VALUE
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
                BluetoothGattService(BatteryService.SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Battery Level
            val characteristic = BluetoothGattCharacteristic(
                BatteryService.CHARACTERISTIC_BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                GattService.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            clientCharacteristicConfigurationDescriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)
            while (!service.addCharacteristic(characteristic));
            return service
        }
    }


    init {
        val applicationContext = context.applicationContext

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
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        Log.d(TAG, "bluetoothLeAdvertiser: $bluetoothLeAdvertiser")
        if (bluetoothLeAdvertiser == null) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }
        serverCallback = GattServerCallback(applicationContext, bluetoothLeAdvertiser, reportMap)
        val gattServer = bluetoothManager.openGattServer(applicationContext, serverCallback)
            ?: throw UnsupportedOperationException("gattServer is null, check Bluetooth is ON.")

        serverCallback?.gattServer = gattServer
        serverCallback?.addService(setUpDeviceInformationService())
        serverCallback?.addService(setUpHidService(needInputReport, needOutputReport, needFeatureReport))
        serverCallback?.addService(setUpBatteryService())
        serverCallback?.startAdvertising()
        serverCallback?.startReportingNotifications(dataSendingRate, inputReportQueue, inputReportCharacteristic)

    }

    fun stopAdvertising() {
        serverCallback?.stopAdvertising()
    }
}