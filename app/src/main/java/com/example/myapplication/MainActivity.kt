package com.example.myapplication

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private var requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it) {
                Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.keyboard_button).setOnKeyListener(KeyboardSender())
        askForPermissions()
    }

    private fun askForPermissions() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_DENIED) {
            requestPermissionsLauncher.launch(ACCESS_FINE_LOCATION)
        }
    }

    fun sendText(view: View) {
        if (view.requestFocus()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        } else {
            Log.e(tag, "Cannot acquire focus")
        }
    }

    fun showBluetoothStatus(view: View) {
        startActivity(Intent(this, BluetoothStatusActivity::class.java))
    }
}