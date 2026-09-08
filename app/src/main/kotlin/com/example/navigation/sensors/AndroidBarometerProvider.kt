package com.example.navigation.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.example.navigation.core.MonotonicTimestamp

fun interface BarometerListener {
    fun onBarometerSample(sample: BarometerSample)
}

/** Collects atmospheric pressure where the device exposes a pressure sensor. */
class AndroidBarometerProvider(
    private val sensorManager: SensorManager,
    callbackLooper: Looper,
    private val listener: BarometerListener
) {
    private val callbackHandler = Handler(callbackLooper)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private var started = false
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            listener.onBarometerSample(BarometerSample(
                pressureHectopascals = event.values[0],
                timestamp = MonotonicTimestamp(event.timestamp)
            ))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /** Returns false when the device has no barometer or registration fails. */
    fun start(): Boolean {
        check(!started) { "Barometer provider is already started" }
        if (sensor == null) return false
        started = sensorManager.registerListener(
            sensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            callbackHandler
        )
        return started
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(sensorListener)
        started = false
    }
}
