package com.example.navigation.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.example.navigation.core.MonotonicTimestamp

fun interface GameRotationVectorListener {
    fun onGameRotationVectorSample(sample: GameRotationVectorSample)
}

/**
 * Collects TYPE_GAME_ROTATION_VECTOR events in Android quaternion order.
 * x/y/z and optional w describe rotation in the Android device coordinate
 * frame (+X right, +Y up, +Z out of screen); the reference is not north-aligned.
 */
class AndroidGameRotationVectorProvider(
    private val sensorManager: SensorManager,
    callbackLooper: Looper,
    private val listener: GameRotationVectorListener,
    private val accuracyListener: (Int) -> Unit = {}
) {
    private val callbackHandler = Handler(callbackLooper)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private var started = false
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            listener.onGameRotationVectorSample(GameRotationVectorSample(
                x = values[0],
                y = values[1],
                z = values[2],
                scalar = values.getOrNull(3),
                timestamp = MonotonicTimestamp(event.timestamp)
            ))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = accuracyListener(accuracy)
    }

    /** Returns false when the device does not provide a game rotation vector. */
    fun start(): Boolean {
        check(!started) { "Game rotation vector provider is already started" }
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
