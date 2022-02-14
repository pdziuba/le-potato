package com.le.potato.utils

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import com.le.potato.R

object BluetoothEnabler {
    class EnableBluetoothContract :
        ActivityResultContract<Unit, Int>() {

        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Int {
            return resultCode
        }
    }

    fun enableBluetoothOrFinish(activity: ComponentActivity) {
        val enableBluetoothActivity = activity.registerForActivityResult(EnableBluetoothContract()) { enabled ->
            if (enabled == Activity.RESULT_CANCELED) {
                Toast.makeText(activity, activity.getString(R.string.bluetooth_disabled_error), Toast.LENGTH_SHORT).show()
            }
        }
        enableBluetoothActivity.launch(Unit)
    }
}