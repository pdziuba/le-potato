package com.le.potato

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.widget.TextView
import com.le.potato.transport.BluetoothFacadeService

object StatusMixin {
    fun setStatusText(statusTextView: TextView, bluetoothService: BluetoothFacadeService?, bluetoothAdapter: BluetoothAdapter?, context: Context) {
        when {
            bluetoothAdapter?.isEnabled != true -> {
                statusTextView.text = context.getString(R.string.bluetooth_disabled)
            }
            bluetoothService?.connectedDevice != null -> {
                statusTextView.text =
                    context.getString(R.string.status_connected, bluetoothService.connectedDevice?.name)
            }
            bluetoothService?.connectingDevice != null -> {
                statusTextView.text = context.getString(R.string.status_connecting)
            }
            bluetoothService?.isAdvertising == true -> {
                statusTextView.text = context.getString(R.string.status_advertising)
            }
            bluetoothService?.isScanning == true -> {
                statusTextView.text = context.getString(R.string.status_scanning)
            }
            else -> {
                statusTextView.text = context.getString(R.string.status_idle)
            }
        }
    }
}