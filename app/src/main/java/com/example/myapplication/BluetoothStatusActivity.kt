package com.example.myapplication

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.bledevices.BLEDevicesListAdapter


const val SCAN_PERIOD: Long = 10000

class BluetoothStatusActivity : AppCompatActivity(),
    BLEDevicesListAdapter.ItemClickListener {
    private val tag = "BluetoothStatusActivity"
    private var bleDevicesListAdapter: BLEDevicesListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private var mHandler: Handler? = null

    class EnableBluetoothContract :
        ActivityResultContract<Unit, Int>() {

        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Int {
            return resultCode
        }
    }
    private val enableBluetoothActivity = registerForActivityResult(EnableBluetoothContract()) { enabled ->
        if (enabled == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "Nah! I can't do my job without bluetooth enabled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_status)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "No bluetooth on board found :(", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mHandler = Handler(this.mainLooper)

        val recyclerView: RecyclerView = findViewById(R.id.ble_devices)
        bleDevicesListAdapter = BLEDevicesListAdapter(this)
        bleDevicesListAdapter?.clickListener = this
        recyclerView.adapter = bleDevicesListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null) {
            // fuck this
            finish()
            return
        }
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter!!.isEnabled) {
            enableBluetoothActivity.launch(Unit)
        }
        else {
            loadBluetoothStatus()
            scanLeDevice(true)
        }
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        bleDevicesListAdapter?.clear()
    }

    private val mLeScanCallback = object: ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (result != null && result.device != null) {
            bleDevicesListAdapter?.addDevice(result.device)
        }
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        if (results != null) {
            for (result in results) {
                if (result.device != null) {
                    bleDevicesListAdapter?.addDevice(result.device)
                }
            }
        }
    }

    override fun onScanFailed(errorCode: Int) {
        Log.e("ble", "Scan failed with error code $errorCode")
    }
    }

    private fun scanLeDevice(enable: Boolean) {
        val bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler!!.postDelayed({
                scanning = false
                bleScanner.stopScan(mLeScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bleScanner.startScan(mLeScanCallback)
        } else {
            scanning = false
            bleScanner.stopScan(mLeScanCallback)
        }
    }


    private fun loadBluetoothStatus() {
        if (bluetoothAdapter != null) {
            val isBluetoothSupported = true
            val isBluetoothEnabled = bluetoothAdapter!!.isEnabled
            val isBLESupported =
                packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            val deviceName = bluetoothAdapter!!.name
            findViewById<CheckBox>(R.id.bluetooth_available_checkbox).isChecked =
                isBluetoothSupported
            findViewById<CheckBox>(R.id.bluetooth_on_checkbox).isChecked = isBluetoothEnabled
            findViewById<CheckBox>(R.id.ble_checkbox).isChecked = isBLESupported
            findViewById<TextView>(R.id.device_name_text_view).text = deviceName
        }
    }


    override fun onItemClick(view: View?, position: Int) {
        Toast.makeText(this, bleDevicesListAdapter?.getItem(position), Toast.LENGTH_SHORT).show()
    }
}