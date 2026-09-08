package com.example.navigation.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

fun interface GyroscopeListener {
    fun onGyroscopeSample(sample: GyroscopeSample)
}

/** Collects Android gyroscope events at an explicitly configured target rate. */
class AndroidGyroscopeProvider(
    private val sensorManager: SensorManager,
    callbackLooper: Looper,
    private val listener: GyroscopeListener,
    val targetHz: Int = 50
) {
    private val callbackHandler = Handler(callbackLooper)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var started = false
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            listener.onGyroscopeSample(GyroscopeSample(event.values[0], event.values[1], event.values[2],
                com.example.navigation.core.MonotonicTimestamp(event.timestamp)))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    init { require(targetHz in SUPPORTED_RATES_HZ) { "targetHz must be one of 25, 50, or 100" } }

    /** Returns false when the device has no gyroscope or registration fails. */
    fun start(): Boolean {
        check(!started) { "Gyroscope provider is already started" }
        if (sensor == null) return false
        started = sensorManager.registerListener(sensorListener, sensor, 1_000_000 / targetHz, callbackHandler)
        return started
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(sensorListener)
        started = false
    }

    private companion object { val SUPPORTED_RATES_HZ = setOf(25, 50, 100) }
}
