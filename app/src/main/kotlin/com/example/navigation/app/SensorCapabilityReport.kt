package com.example.navigation.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build

/** Snapshot of hardware/API capabilities collected at application startup. */
data class SensorCapabilityReport(
    val gyroscope: Boolean,
    val accelerometer: Boolean,
    val gameRotationVector: Boolean,
    val magnetometer: Boolean,
    val barometer: Boolean,
    val rawGnss: Boolean,
    val agc: Boolean,
    val carrierFrequencies: Boolean,
    val androidVersion: Int,
    val deviceModel: String
)

object SensorCapabilityCollector {
    fun collect(context: Context): SensorCapabilityReport {
        val sensors = context.getSystemService(SensorManager::class.java)
        val has = { type: Int -> sensors?.getDefaultSensor(type) != null }
        val gpsAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        // Android exposes raw measurements from API 24; AGC and carrier-frequency fields were added in API 26.
        val rawGnss = gpsAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        val extendedGnss = rawGnss && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        return SensorCapabilityReport(
            gyroscope = has(Sensor.TYPE_GYROSCOPE),
            accelerometer = has(Sensor.TYPE_ACCELEROMETER),
            gameRotationVector = has(Sensor.TYPE_GAME_ROTATION_VECTOR),
            magnetometer = has(Sensor.TYPE_MAGNETIC_FIELD),
            barometer = has(Sensor.TYPE_PRESSURE),
            rawGnss = rawGnss,
            agc = extendedGnss,
            carrierFrequencies = extendedGnss,
            androidVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }
}
