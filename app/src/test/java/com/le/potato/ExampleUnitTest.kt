package com.le.potato

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
fun ByteArray.toHexString() = joinToString(",") { "%02x".format(it) }

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val reportTypesCount: Int = 1
        for (reportId in 1..reportTypesCount) {
            println("Reportid = $reportId")
        }

    }
}