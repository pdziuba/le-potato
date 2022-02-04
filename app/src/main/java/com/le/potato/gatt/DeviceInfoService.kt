package com.le.potato.gatt

import android.bluetooth.*
import com.le.potato.BleUuidUtils
import java.nio.charset.StandardCharsets
import java.util.*

private const val DEVICE_INFO_MAX_LENGTH = 20

class DeviceInfoService(manufacturer: String, deviceName: String, serialNumber: String): AbstractGattServiceHandler() {
    private val manufacturer = clampStringLength(manufacturer)
    private val deviceName = clampStringLength(deviceName)
    private val serialNumber = clampStringLength(serialNumber)

    companion object {
        val SERVICE_DEVICE_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x180A)
        val CHARACTERISTIC_MANUFACTURER_NAME: UUID = BleUuidUtils.fromShortValue(0x2A29)
        val CHARACTERISTIC_MODEL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A24)
        val CHARACTERISTIC_SERIAL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A25)
        val CHARACTERISTIC_PNP_ID: UUID = BleUuidUtils.fromShortValue(0x2A50)

        // PNP_id with vendor id of Samsung
        val CHARACTERISTIC_PNP_ID_VALUE = byteArrayOf(1, -37, -3, 0, 0, 0, 0)
    }

    private fun clampStringLength(value: String): String {
        val deviceNameBytes = value.toByteArray(StandardCharsets.UTF_8)
        return if (deviceNameBytes.size > DEVICE_INFO_MAX_LENGTH) {
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
            value
        }
    }

    override fun setup(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_DEVICE_INFORMATION,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_MANUFACTURER_NAME,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ))

        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_MODEL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ))

        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_SERIAL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ))

        addCharacteristic(service, BluetoothGattCharacteristic(
            CHARACTERISTIC_PNP_ID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ))

        return service
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
        gattServer: BluetoothGattServer
    ): Boolean {
        when(characteristic.uuid) {
            CHARACTERISTIC_MANUFACTURER_NAME -> {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    manufacturer.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                return true
            }
            CHARACTERISTIC_SERIAL_NUMBER -> {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    serialNumber.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                return true
            }
            CHARACTERISTIC_MODEL_NUMBER -> {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    deviceName.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                return true
            }
            CHARACTERISTIC_PNP_ID -> {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    CHARACTERISTIC_PNP_ID_VALUE
                )
                return true
            }
        }
        return false
    }
}