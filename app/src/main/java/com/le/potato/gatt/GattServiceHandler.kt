package com.le.potato.gatt

import android.bluetooth.*
import com.le.potato.utils.UuidUtils
import java.lang.IllegalStateException
import java.util.*

interface GattServiceHandler {
    fun setup(): BluetoothGattService?
    fun addInputReport(reportId: Int, inputReport: ByteArray?)
    fun pollInputReportQueue(): Pair<BluetoothGattCharacteristic?, ByteArray?>
    fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
        gattServer: BluetoothGattServer
    ): Boolean
    fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
        gattServer: BluetoothGattServer
    ): Boolean
    fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
        gattServer: BluetoothGattServer
    ): Boolean
    fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
        gattServer: BluetoothGattServer
    ): Boolean
}

abstract class AbstractGattServiceHandler: GattServiceHandler {
    override fun pollInputReportQueue(): Pair<BluetoothGattCharacteristic?, ByteArray?> {
        return Pair(null, null)
    }

    protected fun addCharacteristic(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic) {
        var tries = 0
        var success = false
        while (!success && tries < 3) {
            success = service.addCharacteristic(characteristic)
            tries += 1
        }
        if (!success) {
            throw IllegalStateException("Failed to add characteristic ${characteristic.uuid} to service ${service.uuid}")
        }
    }

    override fun addInputReport(reportId: Int, inputReport: ByteArray?) {}

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
        gattServer: BluetoothGattServer
    ): Boolean {
        return false
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
        gattServer: BluetoothGattServer
    ): Boolean {
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
        return false
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
        return false
    }

    companion object {
        val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION: UUID = UuidUtils.fromShortValue(0x2902)
    }
}