package com.le.potato.transport

import android.bluetooth.BluetoothDevice

interface DeviceConnectedListener {
    fun onDeviceConnected(device: BluetoothDevice)
    fun onDeviceConnecting(device: BluetoothDevice)
    fun onDeviceDisconnected(device: BluetoothDevice)
}