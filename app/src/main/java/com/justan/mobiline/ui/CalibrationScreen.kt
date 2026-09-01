package com.justan.mobiline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justan.mobiline.sensors.CalibrationOffset
import com.justan.mobiline.sensors.OrientationRepository
import com.justan.mobiline.sensors.Orientation
import com.justan.mobiline.sensors.SmoothedValue
import kotlin.math.abs

/**
 * Step 1: what calibration is actually for (for future-me and anyone else
 * reading this code) --
 *
 * Your phone is never mounted perfectly flat inside its holder/case. There's
 * always a tiny bit of tilt from the fit of the mount itself. Calibration
 * measures that built-in tilt against a surface you already know is flat and
 * level (a table, countertop, or floor -- checked with a bubble level or a
 * level app if you're not sure) and stores it as an offset. Every later
 * reading subtracts this offset, so what you see on the Camber/Toe screens is
 * the wheel's real angle, not "wheel angle + however crooked the phone sits
 * in its holder."
 *
 * TWO-POSITION METHOD:
 * A single reading can't separate "phone is crooked in its holder" from
 * "surface isn't quite level." So we take TWO readings:
 *   1. measure with the top of the phone pointing in some direction
 *   2. rotate the phone 180° in place (still flat) and measure again
 * The mount tilt reverses sign between the two, while anything real stays put.
 * Half the difference between the two readings is the phone's built-in tilt;
 * half the sum is the surface's own tilt, which we ignore because we ask you
 * to calibrate on a level surface.
 *
 * This step is NOT performed against the car or the wheel -- you don't yet
 * know the wheel is a valid zero point, that's the whole thing you're about
 * to go measure.
 */
@Composable
fun CalibrationScreen(
    repository: OrientationRepository,
    onCalibrated: (CalibrationOffset) -> Unit
) {
    var latest by remember { mutableStateOf(Orientation(0f, 0f, 0f)) }
    var firstReading by remember { mutableStateOf<Orientation?>(null) }
    val smoothedRoll = remember { SmoothedValue(alpha = 0.15f) }
    val smoothedPitch = remember { SmoothedValue(alpha = 0.15f) }
    val smoothedYaw = remember { SmoothedValue(alpha = 0.15f) }

    LaunchedEffect(Unit) {
        repository.orientationFlow().collect { reading ->
            val roll = smoothedRoll.update(reading.roll)
            val pitch = smoothedPitch.update(reading.pitch)
            val yaw = smoothedYaw.update(reading.yaw)
            latest = Orientation(roll, pitch, yaw)
        }
    }

    // Purely a "hold it still" indicator -- confirms the readings have
    // settled down before you lock in the calibration, not that the
    // surface itself is level (you're responsible for that part).
    val isSteady = abs(latest.roll) < 90f // sensor is producing sane values

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Step 1: Calibrate",
            fontWeight = FontWeight.Bold
        )

        if (firstReading == null) {
            Text(
                text = "\nFind a flat, level surface -- a table, countertop, " +
                    "or the floor works fine. If you're not sure it's level, " +
                    "check it with a bubble level or a \"level\" app first.\n\n" +
                    "Do NOT use the car or a wheel for this step.\n\n" +
                    "Place your phone (in its holder) flat on that surface, " +
                    "screen facing up toward the sky. Leave it still for a " +
                    "couple of seconds, then tap \"Capture 1 of 2\" below.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "\nGood. Now ROTATE the phone 180° in place -- keep it " +
                    "flat on the surface and turn it around so the TOP of the " +
                    "phone points the opposite way. Leave it still for a " +
                    "couple of seconds, then tap \"Capture 2 of 2\" below.\n\n" +
                    "This second, rotated reading cancels out the phone's " +
                    "built-in tilt in its holder.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "\nWhy? Your phone never sits perfectly flat inside its " +
                "holder. Two readings, taken with the phone rotated 180° " +
                "between them, let the app separate the phone's tilt from " +
                "anything real, so every later measurement is corrected for it.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.Gray
        )

        Text(
            text = "\nCurrent reading: %.1f°".format(latest.roll),
            color = if (isSteady) Color(0xFF2E7D32) else Color(0xFFC62828)
        )

        Button(onClick = {
            val first = firstReading
            if (first == null) {
                firstReading = latest
            } else {
                // Tilt bias = half the difference between the two positions.
                // Yaw has no meaningful offset here (a level surface gives no
                // heading reference), and toe is computed from differences, so
                // any constant yaw shift cancels out anyway.
                onCalibrated(
                    CalibrationOffset(
                        rollOffset = (first.roll - latest.roll) / 2f,
                        pitchOffset = (first.pitch - latest.pitch) / 2f,
                        yawOffset = 0f
                    )
                )
            }
        }) {
            Text(if (firstReading == null) "Capture 1 of 2" else "Capture 2 of 2")
        }

        if (firstReading != null) {
            TextButton(onClick = { firstReading = null }) {
                Text("Start over")
            }
        }
    }
}