package com.le.potato.bledevices

import android.bluetooth.BluetoothDevice

class BTDeviceWrapper(val device: BluetoothDevice, val connectionState: Int) {
    val name: String?
        get() = device.name
    val address: String?
        get() = device.address
}