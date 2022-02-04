package com.le.potato.gatt

import android.bluetooth.*

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
}