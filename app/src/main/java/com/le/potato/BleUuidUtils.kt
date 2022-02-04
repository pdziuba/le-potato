package com.le.potato

import java.util.*

object BleUuidUtils {
    fun fromShortValue(uuidShortValue: Int): UUID {
        return UUID.fromString("0000${"%04X".format(uuidShortValue)}-0000-1000-8000-00805F9B34FB")
    }
}