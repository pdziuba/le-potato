package com.le.potato.gatt

import android.bluetooth.*
import com.le.potato.utils.UuidUtils
import java.util.*

class BatteryService: AbstractGattServiceHandler() {

    companion object {
        val SERVICE_BATTERY: UUID = UuidUtils.fromShortValue(0x180F)
        val CHARACTERISTIC_BATTERY_LEVEL: UUID = UuidUtils.fromShortValue(0x2A19)
    }
    override fun setup(): BluetoothGattService {
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
        addCharacteristic(service, characteristic)
        return service
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
        gattServer: BluetoothGattServer
    ): Boolean {
        if (CHARACTERISTIC_BATTERY_LEVEL == characteristic.uuid) {
            gattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                byteArrayOf(0x64)
            ) // always 100%
            return true
        }
        return false
    }
}