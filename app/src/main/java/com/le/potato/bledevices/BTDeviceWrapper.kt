package com.le.potato.bledevices

import android.bluetooth.BluetoothDevice

class BTDeviceWrapper(val device: BluetoothDevice, val connectionState: Int) {
    val name: String?
        get() = device.name
    val address: String?
        get() = device.address

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BTDeviceWrapper

        if (device != other.device) return false

        return true
    }

    override fun hashCode(): Int {
        return device.hashCode()
    }


}