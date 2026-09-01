package com.justan.mobiline.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Roll / pitch / yaw in degrees, derived from the fused rotation sensor.
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

    /**
     * TYPE_GAME_ROTATION_VECTOR fuses only the accelerometer + gyroscope --
     * NO magnetometer. All of the toe math in this app is a DIFFERENCE
     * between two headings (wheel vs. base, or left wheel vs. right
     * wheel), never an absolute compass direction, so we never actually
     * needed true north. That makes this sensor a much better fit than
     * TYPE_ROTATION_VECTOR: a car's steel body bends the magnetic field
     * differently within inches of itself (wheel wells, the engine block,
     * speakers, the alternator...), which was causing yaw to swing wildly
     * right next to the car even though the same phone was rock-steady a
     * few feet away on a bench. Dropping the magnetometer from the fusion
     * removes that source of error entirely.
     *
     * The trade-off: without a magnetometer anchor, yaw can drift slowly
     * from gyroscope bias over many minutes. That drift is much smaller
     * and much slower than near-car magnetic distortion, and is a
     * non-issue over the few minutes a measurement session takes.
     *
     * Devices without a gyroscope don't expose this sensor at all, so we
     * fall back to the old magnetometer-fused TYPE_ROTATION_VECTOR on
     * those devices -- see [usesMagnetometerForHeading].
     */
    private val gameRotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val magnetometerRotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val activeSensor: Sensor? = gameRotationSensor ?: magnetometerRotationSensor

    /**
     * True if this device has no gyroscope and is therefore falling back
     * to the magnetometer-fused sensor -- toe readings taken close to a
     * car's steel body may be noisier/less stable on these devices.
     */
    fun usesMagnetometerForHeading(): Boolean =
        activeSensor != null && activeSensor === magnetometerRotationSensor

    /**
     * Emits a continuous stream of orientation readings from whichever
     * sensor is active (game rotation vector, preferred; magnetometer
     * rotation vector as a fallback). Both report the same rotation-vector
     * format, so the decoding below is identical either way.
     */
    fun orientationFlow(): Flow<Orientation> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // orientationAngles: [0]=azimuth(yaw) [1]=pitch [2]=roll -- all in radians.
                // With the game rotation vector this "azimuth" isn't true
                // compass north, just an arbitrary but internally
                // consistent zero point -- which is all the toe/camber
                // math (always a difference between two readings) needs.
                val yawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                trySend(Orientation(roll = rollDeg, pitch = pitchDeg, yaw = yawDeg))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        activeSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }

    fun hasRotationSensor(): Boolean = activeSensor != null
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
