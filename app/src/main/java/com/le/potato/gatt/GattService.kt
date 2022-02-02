package com.le.potato.gatt

import android.bluetooth.*
import android.util.Log
import com.le.potato.BleUuidUtils
import java.util.*

class GattService : AbstractGattServiceHandler() {
    private val tag: String = "GattService"
    private val emptyBytes = byteArrayOf()
    override fun setup(): BluetoothGattService? {
        return null
    }

    companion object {
        val DESCRIPTOR_REPORT_REFERENCE: UUID = BleUuidUtils.fromShortValue(0x2908)
        val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION: UUID = BleUuidUtils.fromShortValue(0x2902)
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
        gattServer: BluetoothGattServer
    ): Boolean {
        if (BleUuidUtils.matches(DESCRIPTOR_REPORT_REFERENCE, descriptor.uuid)) {
            when (descriptor.characteristic.properties) {
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY -> {
                    // Input Report
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 1)
                    )
                }
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE -> {
                    // Output Report
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(0, 2)
                    )
                }
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE -> {
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
        } else if (BleUuidUtils.matches(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                descriptor.uuid
            )
        ) {
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
            if (BleUuidUtils.matches(
                    DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    descriptor.uuid
                )
            ) {
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