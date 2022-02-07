package com.le.potato.utils

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import java.util.HashMap

val connStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
    init {
        put(BluetoothProfile.STATE_CONNECTED, "STATE_CONNECTED")
        put(BluetoothProfile.STATE_DISCONNECTED, "STATE_DISCONNECTED")
        put(BluetoothProfile.STATE_CONNECTING, "STATE_CONNECTING")
        put(BluetoothProfile.STATE_DISCONNECTING, "STATE_DISCONNECTING")
    }
}
val bondStateLabels: Map<Int, String> = object : HashMap<Int, String>() {
    init {
        put(BluetoothDevice.BOND_NONE, "BOND_NONE")
        put(BluetoothDevice.BOND_BONDING, "BOND_BONDING")
        put(BluetoothDevice.BOND_BONDED, "BOND_BONDED")
    }
}