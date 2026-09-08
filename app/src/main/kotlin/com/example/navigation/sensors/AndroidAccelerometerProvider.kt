package com.example.navigation.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.example.navigation.core.MonotonicTimestamp

fun interface AccelerometerListener {
    fun onAccelerometerSample(sample: AccelerometerSample)
}

/** Collects accelerometer samples for downstream step/motion analysis only. */
class AndroidAccelerometerProvider(
    private val sensorManager: SensorManager,
    callbackLooper: Looper,
    private val listener: AccelerometerListener
) {
    private val callbackHandler = Handler(callbackLooper)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var started = false
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            listener.onAccelerometerSample(AccelerometerSample(
                xMetersPerSecondSquared = event.values[0],
                yMetersPerSecondSquared = event.values[1],
                zMetersPerSecondSquared = event.values[2],
                timestamp = MonotonicTimestamp(event.timestamp)
            ))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /** Returns false when the device has no accelerometer or registration fails. */
    fun start(): Boolean {
        check(!started) { "Accelerometer provider is already started" }
        if (sensor == null) return false
        started = sensorManager.registerListener(
            sensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
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
