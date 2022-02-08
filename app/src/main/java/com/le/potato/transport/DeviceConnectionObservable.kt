package com.le.potato.transport

import android.bluetooth.BluetoothDevice

interface DeviceConnectionObservable {
    fun registerDeviceConnectedListener(deviceConnectedListener: DeviceConnectedListener)
    fun unregisterDeviceConnectedListener(deviceConnectedListener: DeviceConnectedListener)
    fun fireDeviceConnectedEvent(device: BluetoothDevice)
    fun fireDeviceDisconnectedEvent(device: BluetoothDevice)
    fun fireDeviceConnectingEvent(device: BluetoothDevice)
    fun fireDeviceConnectionErrorEvent(device: BluetoothDevice, error: String?)
}