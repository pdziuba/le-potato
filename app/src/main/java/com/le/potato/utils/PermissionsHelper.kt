package com.le.potato.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

interface PermissionsResolvedListener {
    fun permissionsResolved(granted: Boolean)
}

class PermissionsHelper(private val activity:ComponentActivity, private val permissionsListener: PermissionsResolvedListener) {
    private val tag = PermissionsHelper::class.java.simpleName

    private var requestPermissionsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            var allGranted = true
            for ((permissionName, granted) in it) {
                if (!granted) {
                    allGranted = false
                    Log.i(tag, "Permission $permissionName was not granted")
                    permissionsListener.permissionsResolved(false)
                    break
                }
            }
            if (allGranted) {
                permissionsListener.permissionsResolved(true)
            }
        }
    fun askForPermissions() {
        val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_DENIED
        }
        Log.i(tag, "About to launch permission request for $missingPermissions")
        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            permissionsListener.permissionsResolved(true)
        }
    }
}