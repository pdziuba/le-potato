package com.example.myapplication

import java.util.*

object GattService {
    val DESCRIPTOR_REPORT_REFERENCE: UUID = BleUuidUtils.fromShortValue(0x2908)
    val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION: UUID = BleUuidUtils.fromShortValue(0x2902)
}

object HIDService {
    val SERVICE_BLE_HID: UUID = BleUuidUtils.fromShortValue(0x1812)
    val CHARACTERISTIC_HID_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x2A4A)
    val CHARACTERISTIC_REPORT_MAP: UUID = BleUuidUtils.fromShortValue(0x2A4B)
    val CHARACTERISTIC_HID_CONTROL_POINT: UUID = BleUuidUtils.fromShortValue(0x2A4C)
    val CHARACTERISTIC_REPORT: UUID = BleUuidUtils.fromShortValue(0x2A4D)
    val CHARACTERISTIC_PROTOCOL_MODE: UUID = BleUuidUtils.fromShortValue(0x2A4E)

    val CHARACTERISTIC_HID_INFORMATION_VALUE = byteArrayOf(0x11, 0x01, 0x00, 0x03)
}

object BatteryService {
    val SERVICE_BATTERY: UUID = BleUuidUtils.fromShortValue(0x180F)
    val CHARACTERISTIC_BATTERY_LEVEL: UUID = BleUuidUtils.fromShortValue(0x2A19)
}

object DeviceInfoService {
    val SERVICE_DEVICE_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x180A)
    val CHARACTERISTIC_MANUFACTURER_NAME: UUID = BleUuidUtils.fromShortValue(0x2A29)
    val CHARACTERISTIC_MODEL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A24)
    val CHARACTERISTIC_SERIAL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A25)
    val CHARACTERISTIC_PNP_ID: UUID = BleUuidUtils.fromShortValue(0x2A50)

    // PNP_id with vendor id of Samsung
    val CHARACTERISTIC_PNP_ID_VALUE = byteArrayOf(1, -37, -3, 0, 0, 0, 0)
}