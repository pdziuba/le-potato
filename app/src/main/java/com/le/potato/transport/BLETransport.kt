package com.le.potato.transport

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import com.le.potato.gatt.BatteryService
import com.le.potato.gatt.DeviceInfoService
import com.le.potato.gatt.GattServiceHandler
import com.le.potato.gatt.HIDService
import com.le.potato.utils.bondStateLabels
import com.le.potato.utils.connStateLabels
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

const val ADVERTISE_TIMEOUT = 60000L

class BLETransport : AbstractHIDTransport() {
    private val tag = BLETransport::class.java.simpleName
    private val serviceSemaphore = Semaphore(1)
    private var _isAdvertising = false
    private var descriptorsRead = false
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private lateinit var hidService: HIDService
    private var connectingDevice: BluetoothDevice? = null
    private val gattServiceHandlers: MutableList<GattServiceHandler> = ArrayList()
    var advertisingListener: AdvertisingListener? = null
    private var reportingTimer: Timer? = null
    val isAdvertising: Boolean
        get() = _isAdvertising

    override fun init(context: Context, reportMap: ByteArray) {
        super.init(context, reportMap)
           if (!bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }
        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
            ?: throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")

        gattServer = bluetoothManager!!.openGattServer(applicationContext, GattServerCallback())
            ?: throw UnsupportedOperationException("gattServer is null, check Bluetooth is ON.")


        gattServiceHandlers.add(BatteryService())
        gattServiceHandlers.add(DeviceInfoService("Samsung", "AmazingKbrd", "123456789"))
        hidService = HIDService(
            needInputReport = true,
            needOutputReport = true,
            needFeatureReport = false,
            reportMap = reportMap,
            reportTypesCount = 2
        )
        gattServiceHandlers.add(hidService)

        for (gattServiceHandler in gattServiceHandlers) {
            val gattService = gattServiceHandler.setup()
            if (gattService != null) {
                addService(gattService)
            }
        }
        startReportingNotifications()
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(btReceiver, intentFilter)
    }

    private val btReceiver = object : BroadcastReceiver() {
        //todo: handle turning bluetooth adapter off/on
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice? ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, Integer.MIN_VALUE)
                    Log.d(tag, "BOND STATE = ${bondStateLabels[bondState]} device = ${bluetoothDevice.address} connecting device = ${connectingDevice?.address}")
                    if (bondState == BluetoothDevice.BOND_BONDED && bluetoothDevice.address == connectingDevice?.address) {
                        finalizeConnection(bluetoothDevice)
                    }
                }
                else -> {
                    Log.d(tag, "Received action ${intent.action} with extras ${intent.extras}")
                }
            }
        }
    }

    override fun addInputReport(reportId: Int, report: ByteArray) {
        hidService.addInputReport(reportId, report)
    }

    override fun disconnect(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            if (connectedDevicesMap.containsKey(device.address)) {
                gattServer?.cancelConnection(device)
                connectedDevicesMap.remove(device.address)
                fireDeviceDisconnectedEvent(device)
            }
        }
    }

    override fun deactivate() {
        stopAdvertising()
        applicationContext?.unregisterReceiver(btReceiver)
        closeGattServer()
        reportingTimer?.cancel()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(tag, "Advertising started successfully :)")
            serviceSemaphore.release()
            advertisingListener?.onAdvertiseStarted()
            handler?.postDelayed({
                stopAdvertising()
            }, ADVERTISE_TIMEOUT)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(tag, "Advertising failure: $errorCode")
            serviceSemaphore.release()
            advertisingListener?.onAdvertiseFailure()
        }
    }

    inner class GattServerCallback : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(
                tag,
                "onConnectionStateChange status: " + status + ", newState: " + connStateLabels[newState]
            )
            if (status != 0) {
                Log.e(tag, "Error on connection state change: $status")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(
                        tag,
                        "BluetoothProfile.STATE_CONNECTED bondState: " + bondStateLabels[device.bondState]
                    )
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        finalizeConnection(device)
                    } else {
                        connectingDevice = device
                        fireDeviceConnectingEvent(device)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val deviceAddress = device.address

                    synchronized(connectedDevicesMap) {
                        connectedDevicesMap.remove(deviceAddress)
                    }
                    descriptorsRead = false
                    fireDeviceDisconnectedEvent(device)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    fireDeviceConnectingEvent(device)
                }
                else -> {}
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(
                tag,
                "onCharacteristicReadRequest characteristic: ${characteristic.uuid}, offset: $offset"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                var handled = false
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onCharacteristicReadRequest(
                            device,
                            requestId,
                            offset,
                            characteristic,
                            gattServer
                        )
                    ) {
                        handled = true
                        break
                    }
                }
                if (!handled) {
                    Log.w(
                        tag,
                        "Responding with implicit characteristic value. UUID=${characteristic.uuid} val=${characteristic.value}"
                    )
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        characteristic.value
                    )
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.d(
                tag,
                "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onDescriptorReadRequest(device, requestId, offset, descriptor, gattServer)) {
                        descriptorsRead = true
                        break
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            reqId: Int,
            characteristic: BluetoothGattCharacteristic,
            prepWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, reqId, characteristic, prepWrite, responseNeeded, offset, value)
            Log.d(
                tag,
                "onCharacteristicWriteRequest characteristic: ${characteristic.uuid}, value: ${value.contentToString()}"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onCharacteristicWriteRequest(
                            device,
                            reqId,
                            characteristic,
                            prepWrite,
                            responseNeeded,
                            offset,
                            value,
                            gattServer
                        )
                    ) {
                        break
                    }
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            reqId: Int,
            descriptor: BluetoothGattDescriptor,
            prepWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onDescriptorWriteRequest(device, reqId, descriptor, prepWrite, responseNeeded, offset, value)
            Log.d(
                tag,
                "onDescriptorWriteRequest descriptor: ${descriptor.uuid}, value: ${value.contentToString()}, responseNeeded: $responseNeeded, preparedWrite: $prepWrite"
            )
            val gattServer = gattServer ?: return
            handler?.post {
                for (gattHandler in gattServiceHandlers) {
                    if (gattHandler.onDescriptorWriteRequest(
                            device,
                            reqId,
                            descriptor,
                            prepWrite,
                            responseNeeded,
                            offset,
                            value,
                            gattServer
                        )
                    ) {
                        break
                    }
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.d(tag, "onServiceAdded status: " + status + ", service: " + service.uuid)
            if (status != 0) {
                throw IllegalStateException("Adding service with uuid ${service.uuid} failed")
            }
            serviceSemaphore.release()
        }
    }

    private fun finalizeConnection(device: BluetoothDevice) {
        synchronized(connectedDevicesMap) {
            connectedDevicesMap.put(device.address, device)
        }
        connectingDevice = null
        stopAdvertising()
        fireDeviceConnectedEvent(device)
    }

    private fun addService(service: BluetoothGattService) {
        var serviceAdded = false
        var tries = 0

        handler?.post {
            if (serviceSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                while (!serviceAdded && tries < 3) {
                    serviceAdded = gattServer!!.addService(service)
                    tries += 1
                }
            } else {
                throw java.lang.IllegalStateException("Cannot obtain semaphore to add gatt service")
            }
            if (!serviceAdded) {
                throw java.lang.IllegalStateException("Cannot add gatt service ${service.uuid}")
            } else {
                Log.d(tag, "Service: ${service.uuid} added.")
            }
        }
    }

    fun startAdvertising() {
        if (isAdvertising) {
            Log.w(tag, "Already advertising???")
            return
        }

        handler?.post { // set up advertising setting
            if (!serviceSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                throw java.lang.IllegalStateException("Cannot obtain semaphore to start advertising")
            }
            _isAdvertising = true
            val advertiseSettings = AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .build()
            val serviceUUIDs = gattServer?.services?.map {
                ParcelUuid.fromString(it.uuid.toString())
            } ?: throw java.lang.IllegalStateException("No services present in gatt server")

            val advertiseDataBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)

            // todo: payload is limited to 31 bytes if device name is too long this will explode with power of 1000 suns
            //  we may want to truncate device name somehow or prompt the user
            val scanResultBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)

            for (serviceUuid in serviceUUIDs) {
                advertiseDataBuilder.addServiceUuid(serviceUuid)
                scanResultBuilder.addServiceUuid(serviceUuid)
            }

            bluetoothLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseDataBuilder.build(),
                scanResultBuilder.build(),
                advertiseCallback
            )
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) {
            Log.d(tag, "No advertising, ignoring")
            return
        }
        handler?.post {
            try {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                _isAdvertising = false
                advertisingListener?.onAdvertiseStopped()
            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
        }
    }

    private fun startReportingNotifications(dataSendingRate: Long = 15) {
        // send report each dataSendingRate, if data available
        reportingTimer = Timer()
        reportingTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val gattServer = gattServer ?: return
                for (gattHandler in gattServiceHandlers) {
                    val (inputReportCharacteristic, polled) = gattHandler.pollInputReportQueue()
                    if (polled != null && inputReportCharacteristic != null) {
                        //todo: wait for notificationSent callback in gattServerCallback
                        handler?.post {
                            val devices = devices
                            if (devices.isEmpty()) {
                                Log.d(tag, "No devices to send notification")
                            }
                            inputReportCharacteristic.value = polled
                            for (device in devices) {
                                try {
                                    val result = gattServer.notifyCharacteristicChanged(
                                        device,
                                        inputReportCharacteristic,
                                        false
                                    )
                                    if (!result) {
                                        Log.e(tag, "Failed to send notification. Reason unknown.")
                                    } else {
                                        Log.d(tag, "Notification sent")
                                    }
                                } catch (e: Throwable) {
                                    Log.e(tag, "Error on sending notification ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
            }
        }, 0, dataSendingRate)
    }

    private fun closeGattServer() {
        val gattServer = gattServer ?: return
        handler?.post {
            try {
                for (device in devices) {
                    gattServer.cancelConnection(device)
                }
                gattServer.close()
                this.gattServer = null

            } catch (ignored: IllegalStateException) {
                // BT Adapter is not turned ON
            }
        }
    }


}