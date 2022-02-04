package com.le.potato.gatt

import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.*
import android.util.Log
import com.le.potato.BleUuidUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap

class HIDService(
    private val needInputReport: Boolean,
    private val needOutputReport: Boolean,
    private val needFeatureReport: Boolean,
    private val reportMap: ByteArray,
    private val reportTypesCount: Int = 1
) : AbstractGattServiceHandler() {
    private val tag = "HIDService"
    private val emptyBytes = byteArrayOf()
    // map of reportId -> instanceId
    private val reportIdMap = HashMap<Int, BluetoothGattCharacteristic>()
    private val inputReportQueue: Queue<Pair<BluetoothGattCharacteristic, ByteArray>> = ConcurrentLinkedQueue()
    private var gattService: BluetoothGattService? = null

    companion object {
        val SERVICE_BLE_HID: UUID = BleUuidUtils.fromShortValue(0x1812)
        val CHARACTERISTIC_HID_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x2A4A)
        val CHARACTERISTIC_REPORT_MAP: UUID = BleUuidUtils.fromShortValue(0x2A4B)
        val CHARACTERISTIC_HID_CONTROL_POINT: UUID = BleUuidUtils.fromShortValue(0x2A4C)
        val CHARACTERISTIC_REPORT: UUID = BleUuidUtils.fromShortValue(0x2A4D)
        val CHARACTERISTIC_PROTOCOL_MODE: UUID = BleUuidUtils.fromShortValue(0x2A4E)

        val DESCRIPTOR_REPORT_REFERENCE: UUID = BleUuidUtils.fromShortValue(0x2908)

        val CHARACTERISTIC_HID_INFORMATION_VALUE = byteArrayOf(0x11, 0x01, 0x00, 0x03)
    }

    override fun addInputReport(reportId: Int, inputReport: ByteArray?) {
        if (inputReport != null && inputReport.isNotEmpty()) {
            val characteristic = reportIdMap[reportId]
            if (characteristic != null) {
                inputReportQueue.offer(Pair(characteristic, inputReport))
            }
        }
    }

    override fun setup(): BluetoothGattService {

        val service =
            BluetoothGattService(SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        gattService = service

        // HID Information
        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_HID_INFORMATION,
            PROPERTY_READ,
            PERMISSION_READ_ENCRYPTED
        ))

        // Report Map
        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_REPORT_MAP,
            PROPERTY_READ,
            PERMISSION_READ_ENCRYPTED
        ))

        // Protocol Mode
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_PROTOCOL_MODE,
                PROPERTY_READ or PROPERTY_WRITE_NO_RESPONSE,
                PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = WRITE_TYPE_NO_RESPONSE
            addCharacteristic(service, characteristic)
        }

        // HID Control Point
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_CONTROL_POINT,
                PROPERTY_WRITE_NO_RESPONSE,
                PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = WRITE_TYPE_NO_RESPONSE
            addCharacteristic(service, characteristic)
        }

        // Input Report
        if (needInputReport) {
            for (reportId in 1..reportTypesCount) {
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT,
                    PROPERTY_NOTIFY or PROPERTY_READ or PROPERTY_WRITE,
                    PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED
                )
                val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                    DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
                )
                clientCharacteristicConfigurationDescriptor.value =
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)

                val reportReferenceDescriptor = BluetoothGattDescriptor(
                    DESCRIPTOR_REPORT_REFERENCE,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
                )
                reportReferenceDescriptor.value = byteArrayOf(reportId.toByte(), 1)
                characteristic.addDescriptor(reportReferenceDescriptor)
                addCharacteristic(service, characteristic)
                reportIdMap[reportId] = characteristic
            }
        }

        // Output Report
        if (needOutputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                PROPERTY_READ or PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE,
                PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = WRITE_TYPE_NO_RESPONSE
            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            addCharacteristic(service, characteristic)
        }

        // Feature Report
        if (needFeatureReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                PROPERTY_READ or PROPERTY_WRITE,
                PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED
            )
            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)
            addCharacteristic(service, characteristic)
        }
        return service
    }

    override fun pollInputReportQueue(): Pair<BluetoothGattCharacteristic?, ByteArray?> {
        val polled = inputReportQueue.poll()
        if (polled != null){
            return polled
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
        if (CHARACTERISTIC_HID_INFORMATION == characteristicUuid) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                CHARACTERISTIC_HID_INFORMATION_VALUE
            )
            return true
        } else if (CHARACTERISTIC_REPORT_MAP == characteristicUuid) {
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
            return true
        } else if (CHARACTERISTIC_HID_CONTROL_POINT == characteristicUuid) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                byteArrayOf(0)
            )
            return true
        } else if (CHARACTERISTIC_REPORT == characteristicUuid) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                emptyBytes
            )
            return true
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
        if (CHARACTERISTIC_REPORT == characteristic.uuid && responseNeeded) {
            if (characteristic.properties == PROPERTY_READ or PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE) {
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

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
        gattServer: BluetoothGattServer
    ): Boolean {
        if (DESCRIPTOR_REPORT_REFERENCE == descriptor.uuid) {
            when (descriptor.characteristic.properties) {
                PROPERTY_READ or PROPERTY_WRITE or PROPERTY_NOTIFY -> {
                    // Android API sucks like a vacuum cleaner. In SDK <=23 descriptors have instanceId==0 and multiple characteristics with same UUID are not handled correctly :(

                    // Input Report
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        descriptor.value
                    )
                }
                PROPERTY_READ or PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE -> {
                    // Output Report
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 2)
                    )
                }
                PROPERTY_READ or PROPERTY_WRITE -> {
                    // Feature Report
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 3)
                    )
                }
                else -> {
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        emptyBytes
                    )
                }
            }
        } else if (DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION == descriptor.uuid ) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        } else {
            Log.e(
                tag,
                "Unhandled descriptor " + descriptor.uuid + " responding with empty bytes :/"
            )
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                emptyBytes
            )
        }
        return true
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
        gattServer: BluetoothGattServer
    ): Boolean {
        descriptor.value = value
        if (responseNeeded) {
            if (DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION == descriptor.uuid) {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    value
                )
                return true
            }
        }
        return false
    }
}