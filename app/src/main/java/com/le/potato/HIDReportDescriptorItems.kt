package com.le.potato


/**
 * Main items
 */
fun INPUT(size: Int = 1): Byte {
    return (0x80 or size).toByte()
}

fun OUTPUT(size: Int): Byte {
    return (0x90 or size).toByte()
}

fun COLLECTION(size: Int): Byte {
    return (0xA0 or size).toByte()
}

fun FEATURE(size: Int): Byte {
    return (0xB0 or size).toByte()
}

fun END_COLLECTION(size: Int): Byte {
    return (0xC0 or size).toByte()
}

/**
 * Global items
 */

fun USAGE_PAGE(size: Int): Byte {
    return (0x04 or size).toByte()
}

fun LOGICAL_MINIMUM(size: Int): Byte {
    return (0x14 or size).toByte()
}

fun LOGICAL_MAXIMUM(size: Int): Byte {
    return (0x24 or size).toByte()
}

fun PHYSICAL_MINIMUM(size: Int): Byte {
    return (0x34 or size).toByte()
}

fun PHYSICAL_MAXIMUM(size: Int): Byte {
    return (0x44 or size).toByte()
}

fun UNIT_EXPONENT(size: Int): Byte {
    return (0x54 or size).toByte()
}

fun UNIT(size: Int): Byte {
    return (0x64 or size).toByte()
}

fun REPORT_SIZE(size: Int): Byte {
    return (0x74 or size).toByte()
}

fun REPORT_ID(size: Int): Byte {
    return (0x84 or size).toByte()
}

fun REPORT_COUNT(size: Int): Byte {
    return (0x94 or size).toByte()
}

/**
 * Local items
 */

fun USAGE(size: Int): Byte {
    return (0x08 or size).toByte()
}

fun USAGE_MINIMUM(size: Int): Byte {
    return (0x18 or size).toByte()
}

fun USAGE_MAXIMUM(size: Int): Byte {
    return (0x28 or size).toByte()
}


