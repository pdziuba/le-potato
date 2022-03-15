package com.le.potato

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.acra.ACRA

abstract class BaseActivity: AppCompatActivity() {

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.send_logs -> {
                sendLogs()
                true
            }
            R.id.version_info -> {
                showVersionInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showVersionInfo() {
        val versionName = BuildConfig.VERSION_NAME
        Toast.makeText(this, "Version: $versionName", Toast.LENGTH_LONG).show()
    }

    private fun sendLogs() {
        ACRA.errorReporter.handleSilentException(null)
    }

}