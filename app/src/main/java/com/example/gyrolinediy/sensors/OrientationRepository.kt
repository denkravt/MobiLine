package com.example.gyrolinediy.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Roll / pitch / yaw in degrees, derived from the rotation vector sensor.
 *
 * roll  -> tilt left/right   (what we use for CAMBER)
 * pitch -> tilt forward/back
 * yaw   -> heading           (what we use for TOE)
 */
data class Orientation(
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)

class OrientationRepository(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /**
     * Emits a continuous stream of orientation readings.
     * Uses the fused ROTATION_VECTOR sensor (accel + gyro + mag when available)
     * rather than raw gyro integration, so yaw doesn't drift over time.
     */
    fun orientationFlow(): Flow<Orientation> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // orientationAngles: [0]=azimuth(yaw) [1]=pitch [2]=roll -- all in radians
                val yawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                trySend(Orientation(roll = rollDeg, pitch = pitchDeg, yaw = yawDeg))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }

    fun hasRotationSensor(): Boolean = rotationSensor != null
}

/**
 * Simple exponential moving average smoother to tame sensor jitter.
 * Feed it raw readings; read `value` for the smoothed result.
 * alpha closer to 1.0 = more responsive / noisier.
 * alpha closer to 0.0 = smoother / slower to settle.
 */
class SmoothedValue(private val alpha: Float = 0.15f) {
    var value: Float? = null
        private set

    fun update(newReading: Float): Float {
        val prev = value
        val next = if (prev == null) newReading else prev + alpha * (newReading - prev)
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}

/**
 * Holds the zero-point offsets captured during calibration.
 * Subtract these from raw readings to get a "true" angle relative
 * to your reference surface instead of relative to the phone's sensors.
 */
data class CalibrationOffset(
    val rollOffset: Float = 0f,
    val pitchOffset: Float = 0f,
    val yawOffset: Float = 0f
)

fun Orientation.applyOffset(offset: CalibrationOffset): Orientation = Orientation(
    roll = roll - offset.rollOffset,
    pitch = pitch - offset.pitchOffset,
    yaw = yaw - offset.yawOffset
)
