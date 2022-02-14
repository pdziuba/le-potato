package com.le.potato.transport

interface AdvertisingListener {
    fun onAdvertiseStarted()
    fun onAdvertiseStopped()
    fun onAdvertiseFailure(errorCode: Int)
}