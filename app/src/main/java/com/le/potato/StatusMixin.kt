package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.widget.TextView
import com.le.potato.transport.BluetoothFacadeService

object StatusMixin {
    fun setStatusText(statusTextView: TextView, bluetoothService: BluetoothFacadeService?, bluetoothAdapter: BluetoothAdapter?, context: Context) {
        statusTextView.text = when {
            bluetoothAdapter?.isEnabled != true -> {
                context.getString(R.string.bluetooth_disabled)
            }
            bluetoothService?.connectedDevice != null -> {
                context.getString(R.string.status_connected, bluetoothService.connectedDevice?.name)
            }
            bluetoothService?.connectingDevice != null -> {
                context.getString(R.string.status_connecting)
            }
            bluetoothService?.isAdvertising == true -> {
                context.getString(R.string.status_advertising)
            }
            bluetoothService?.isScanning == true -> {
                context.getString(R.string.status_scanning)
            }
            else -> {
                context.getString(R.string.status_idle)
            }
        }
    }
}