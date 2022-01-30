package com.example.myapplication.gatt

import android.bluetooth.*
import com.example.myapplication.BleUuidUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class HIDService(
    private val needInputReport: Boolean,
    private val needOutputReport: Boolean,
    private val needFeatureReport: Boolean,
    private val reportMap: ByteArray,
) : AbstractGattServiceHandler() {
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null
    private val emptyBytes = byteArrayOf()
    private val inputReportQueue: Queue<ByteArray> = ConcurrentLinkedQueue()

    companion object {
        val SERVICE_BLE_HID: UUID = BleUuidUtils.fromShortValue(0x1812)
        val CHARACTERISTIC_HID_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x2A4A)
        val CHARACTERISTIC_REPORT_MAP: UUID = BleUuidUtils.fromShortValue(0x2A4B)
        val CHARACTERISTIC_HID_CONTROL_POINT: UUID = BleUuidUtils.fromShortValue(0x2A4C)
        val CHARACTERISTIC_REPORT: UUID = BleUuidUtils.fromShortValue(0x2A4D)
        val CHARACTERISTIC_PROTOCOL_MODE: UUID = BleUuidUtils.fromShortValue(0x2A4E)

        val CHARACTERISTIC_HID_INFORMATION_VALUE = byteArrayOf(0x11, 0x01, 0x00, 0x03)
    }

    override fun addInputReport(inputReport: ByteArray?) {
        if (inputReport != null && inputReport.isNotEmpty()) {
            inputReportQueue.offer(inputReport)
        }
    }

    override fun setup(): BluetoothGattService {

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
        if (needInputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
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
        if (needOutputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
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
        if (needFeatureReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
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

    override fun pollInputReportQueue(): Pair<BluetoothGattCharacteristic?, ByteArray?> {
        val polled = inputReportQueue.poll()
        if (polled != null){
            return Pair(inputReportCharacteristic, polled)
        }
        return super.pollInputReportQueue()
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
        gattServer: BluetoothGattServer
    ): Boolean {
        val characteristicUuid = characteristic.uuid
        if (BleUuidUtils.matches(CHARACTERISTIC_HID_INFORMATION, characteristicUuid)) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                CHARACTERISTIC_HID_INFORMATION_VALUE
            )
        } else if (BleUuidUtils.matches(CHARACTERISTIC_REPORT_MAP, characteristicUuid)) {
            //todo: handle MTU
            if (offset == 0) {
                gattServer.sendResponse(
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
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        data
                    )
                } else {
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null
                    )
                }
            }
        } else if (BleUuidUtils.matches(CHARACTERISTIC_HID_CONTROL_POINT, characteristicUuid)) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                byteArrayOf(0)
            )
        } else if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristicUuid)) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                emptyBytes
            )
        }
        return false
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
        gattServer: BluetoothGattServer
    ): Boolean {
        if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristic.uuid) && responseNeeded) {
            if (characteristic.properties == BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                //                        onOutputReport(value)
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    emptyBytes
                )
                return true
            }
        }
        return false
    }
}