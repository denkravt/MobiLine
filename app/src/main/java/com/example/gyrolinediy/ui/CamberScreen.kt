package com.example.gyrolinediy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import com.example.gyrolinediy.sensors.CalibrationOffset
import com.example.gyrolinediy.sensors.Orientation
import com.example.gyrolinediy.sensors.OrientationRepository
import com.example.gyrolinediy.sensors.SmoothedValue
import com.example.gyrolinediy.sensors.applyOffset
import kotlin.math.abs

/**
 * Step 2: place the rig against the wheel rim. "roll" (relative to the
 * calibration offset) is your camber angle. Green text = within tolerance
 * of level, matching the "diamond turns green" pattern from commercial tools.
 */
@Composable
fun CamberScreen(
    repository: OrientationRepository,
    calibrationOffset: CalibrationOffset
) {
    var current by remember { mutableStateOf(Orientation(0f, 0f, 0f)) }
    var lockedReading by remember { mutableStateOf<Float?>(null) }

    val smoothedRoll = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedPitch = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedYaw = remember { SmoothedValue(alpha = 0.1f) }

    LaunchedEffect(Unit) {
        repository.orientationFlow().collect { raw ->
            val smoothed = Orientation(
                roll = smoothedRoll.update(raw.roll),
                pitch = smoothedPitch.update(raw.pitch),
                yaw = smoothedYaw.update(raw.yaw)
            )
            current = smoothed.applyOffset(calibrationOffset)
        }
    }

    val camberAngle = current.roll
    // "Level enough" here means the phone isn't tipped forward/backward
    // (pitch), which would throw off the roll reading.
    val isLevelEnough = abs(current.pitch) < 1.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camber", fontWeight = FontWeight.Bold)

        Text(
            text = "\nHold the phone flat against the outer edge of the " +
                "wheel rim (the metal lip, not the tire) so the screen " +
                "faces you and stays upright. Keep it steady -- the " +
                "number below is your camber angle.",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "\n%.2f°".format(camberAngle),
            color = if (isLevelEnough) Color(0xFF2E7D32) else Color(0xFFC62828)
        )

        Text(
            if (isLevelEnough)
                "Looks steady -- tap Save Reading to lock this in."
            else
                "Keep the phone upright (not tipped forward/back) until this turns green."
        )

        Button(onClick = { lockedReading = camberAngle }) {
            Text("Save Reading")
        }

        lockedReading?.let { value ->
            Text("\nSaved camber: %.2f°".format(value))
            Text(
                text = camberGuidance(value),
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Text(
            text = "\nWhat this number means: 0° is straight up-and-down. " +
                "A negative number means the top of the wheel leans in " +
                "toward the car. A positive number means it leans out, " +
                "away from the car.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "\nThis is a ballpark tool, not a substitute for a " +
                "professional alignment rack. If a number looks way off, " +
                "have it double-checked by a shop before making any " +
                "adjustments.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Rough, non-vehicle-specific guidance. Real camber specs vary by make and
 * model (sometimes a lot -- some performance cars run notably negative
 * camber on purpose), so these thresholds are generic passenger-car norms,
 * not "the" correct number for your car. Treat this as a starting point for
 * "does this look unusual," not a target to chase.
 */
private fun camberGuidance(camberDegrees: Float): String = when {
    abs(camberDegrees) <= 0.75f ->
        "This is close to what a lot of passenger cars run as stock -- " +
            "probably not something to worry about as a ballpark check."

    camberDegrees < -0.75f ->
        "This is more negative than a typical passenger-car stock setting " +
            "(top of the wheel leaning inward more than usual). If your " +
            "suspension has a way to adjust camber -- camber bolts, shims, " +
            "or an adjustable control arm/strut mount, depending on the " +
            "vehicle -- that's what you'd loosen and adjust to move the " +
            "top of the wheel outward. Not every car has an adjustment " +
            "built in; some need aftermarket parts."

    else -> // camberDegrees > 0.75f
        "Positive camber this large is unusual for most passenger cars " +
            "and can point to worn or bent suspension parts as much as a " +
            "simple adjustment. If your suspension is adjustable, you'd " +
            "move the top of the wheel inward. Worth comparing against the " +
            "same wheel on the other side of the axle -- a big difference " +
            "between the two sides is more telling than either number " +
            "alone."
}
