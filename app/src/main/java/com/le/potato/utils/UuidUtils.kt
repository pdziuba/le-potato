package com.le.potato.utils

import java.util.*

object UuidUtils {
    fun fromShortValue(uuidShortValue: Int): UUID {
        return UUID.fromString("0000${"%04X".format(uuidShortValue)}-0000-1000-8000-00805F9B34FB")
    }
}