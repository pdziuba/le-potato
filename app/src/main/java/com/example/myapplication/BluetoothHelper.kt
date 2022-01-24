package com.example.myapplication

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract

object BluetoothHelper {
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
                Toast.makeText(activity, "Nah! I can't do my job without bluetooth enabled", Toast.LENGTH_SHORT).show()
                activity.finish()
            }
        }
        enableBluetoothActivity.launch(Unit)
    }
}