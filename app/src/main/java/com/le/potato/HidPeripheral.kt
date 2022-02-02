package com.le.potato

import com.le.potato.gatt.*
import kotlin.collections.ArrayList

abstract class HidPeripheral protected constructor(
    needInputReport: Boolean,
    needOutputReport: Boolean,
    needFeatureReport: Boolean
) {
    private var hidService: HIDService? = null
    val gattServiceHandlers: MutableList<GattServiceHandler> = ArrayList()

    abstract val reportMap: ByteArray

    protected fun addInputReport(inputReport: ByteArray?) {
        hidService?.addInputReport(inputReport)
    }

    protected abstract fun onOutputReport(outputReport: ByteArray?)

    companion object {

        /**
         * Main items
         */
        @JvmStatic
        protected fun INPUT(size: Int = 1): Byte {
            return (0x80 or size).toByte()
        }

        @JvmStatic
        protected fun OUTPUT(size: Int): Byte {
            return (0x90 or size).toByte()
        }

        @JvmStatic
        protected fun COLLECTION(size: Int): Byte {
            return (0xA0 or size).toByte()
        }

        protected fun FEATURE(size: Int): Byte {
            return (0xB0 or size).toByte()
        }

        @JvmStatic
        protected fun END_COLLECTION(size: Int): Byte {
            return (0xC0 or size).toByte()
        }

        /**
         * Global items
         */
        @JvmStatic
        protected fun USAGE_PAGE(size: Int): Byte {
            return (0x04 or size).toByte()
        }

        @JvmStatic
        protected fun LOGICAL_MINIMUM(size: Int): Byte {
            return (0x14 or size).toByte()
        }

        @JvmStatic
        protected fun LOGICAL_MAXIMUM(size: Int): Byte {
            return (0x24 or size).toByte()
        }

        protected fun PHYSICAL_MINIMUM(size: Int): Byte {
            return (0x34 or size).toByte()
        }

        protected fun PHYSICAL_MAXIMUM(size: Int): Byte {
            return (0x44 or size).toByte()
        }

        protected fun UNIT_EXPONENT(size: Int): Byte {
            return (0x54 or size).toByte()
        }

        protected fun UNIT(size: Int): Byte {
            return (0x64 or size).toByte()
        }

        @JvmStatic
        protected fun REPORT_SIZE(size: Int): Byte {
            return (0x74 or size).toByte()
        }

        protected fun REPORT_ID(size: Int): Byte {
            return (0x84 or size).toByte()
        }

        @JvmStatic
        protected fun REPORT_COUNT(size: Int): Byte {
            return (0x94 or size).toByte()
        }

        /**
         * Local items
         */
        @JvmStatic
        protected fun USAGE(size: Int): Byte {
            return (0x08 or size).toByte()
        }

        @JvmStatic
        protected fun USAGE_MINIMUM(size: Int): Byte {
            return (0x18 or size).toByte()
        }

        @JvmStatic
        protected fun USAGE_MAXIMUM(size: Int): Byte {
            return (0x28 or size).toByte()
        }

        protected fun LSB(value: Int): Byte {
            return (value and 0xff).toByte()
        }

        protected fun MSB(value: Int): Byte {
            return (value shr 8 and 0xff).toByte()
        }
    }

    init {
        gattServiceHandlers.add(GattService())
        gattServiceHandlers.add(BatteryService())
        gattServiceHandlers.add(DeviceInfoService("Samsung", "AmazingKbrd", "123456789"))
        hidService = HIDService(needInputReport, needOutputReport, needFeatureReport, reportMap)
        gattServiceHandlers.add(hidService!!)
    }
}