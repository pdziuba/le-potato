package com.le.potato.transport

import android.bluetooth.BluetoothDevice
import android.content.Context

interface HIDTransport {
    fun init(context: Context, reportMap: ByteArray)
    fun addInputReport(reportId: Byte, report: ByteArray)
    fun disconnect(device: BluetoothDevice)
    fun deactivate()
    val devices: Set<BluetoothDevice>
}

