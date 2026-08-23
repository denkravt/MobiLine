package com.example.mobiline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiline.R
import com.example.mobiline.sensors.CalibrationOffset
import com.example.mobiline.sensors.Orientation
import com.example.mobiline.sensors.OrientationRepository
import com.example.mobiline.sensors.SmoothedValue
import com.example.mobiline.sensors.applyOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Toe uses yaw (heading) instead of roll, measured BODY-ALIGN style.
 *
 * WHY BODY REFERENCES (this replaced the old rear-axle-average approach):
 * The phone's compass heading is only as good as the magnetic field around
 * it, and a car's steel bends that field -- differently at each corner.
 * That's why the old "average the rear wheels" reference produced garbage
 * like a 19° rear total toe: it trusted two absolute compass headings taken
 * at opposite sides of a car-sized chunk of steel.
 *
 * The fix is to measure every wheel against a FIXED BODY REFERENCE SURFACE
 * captured on the SAME side moments before the wheel ("you have
 * approximately 20 seconds to take a wheel measurement after refreshing
 * the base" is the rule of thumb). The body spot and the
 * wheel are read back-to-back with the phone in the same position and
 * orientation, so the magnetic distortion -- which is roughly constant
 * across that small area and short time -- is present in BOTH readings and
 * cancels when you subtract them.
 *
 * HOW THE PHONE MUST BE MOUNTED -- this has to be right or every reading
 * is garbage:
 * Place the phone FLAT against the surface (screen facing UP toward the
 * sky) and keep the TOP of the phone pointing toward the FRONT of the car
 * ("Front" at the top of the screen must face the front of the vehicle at
 * all times). Yaw from the
 * rotation-vector sensor is only a true compass heading when the phone is
 * flat; held upright (as Camber does) the number is meaningless, which is
 * the flaw behind the earlier wrong readings.
 *
 * SEQUENCE -- one full side of the car at a time, so you walk around the
 * car once instead of zigzagging left-right-left-right:
 *   1. LEFT body reference (3 readings).
 *   2. Rear-Left:   refresh LEFT  base, then wheel (3 readings).
 *   3. Front-Left:  refresh LEFT  base, then wheel (3 readings).
 *   4. RIGHT body reference (3 readings).
 *   5. Rear-Right:  refresh RIGHT base, then wheel (3 readings).
 *   6. Front-Right: refresh RIGHT base, then wheel (3 readings).
 * The order carries no mathematical weight: each wheel only depends on its
 * own side's base, captured moments earlier. Grouping by side just keeps
 * the walk short and each side's readings close together in time.
 *
 * MATH:
 *   PRIMARY -- TOTAL toe per axle is the DIRECT left-vs-right difference
 *   between the two wheels on that axle. No body reference is involved, so
 *   whatever magnetic distortion the car causes is roughly common to both
 *   readings (same hand hold, seconds apart) and cancels out. After a
 *   possible 180° flip correction, this is the number to trust.
 *
 *   DIAGNOSTIC -- individual wheel toe uses the same-side body reference
 *   (left-side wheels: wheelYaw - leftBaseYaw; right-side: rightBaseYaw -
 *   wheelYaw), each also 180° flip-corrected when it lands beyond ±90°.
 *   These are more sensitive to the phone's direction and are best treated
 *   as a rough cross-check rather than the headline result.
 *
 * Sign convention: positive = toe-in, negative = toe-out.
 *
 * A good body reference spot: the pinch-weld seam under the rocker panel
 * (present on almost any car), a frame rail, or any flat, repeatable body
 * surface that exists on both sides. Use the SAME spot and orientation
 * every time you capture or refresh a base.
 */
@Composable
fun ToeScreen(
    repository: OrientationRepository,
    calibrationOffset: CalibrationOffset
) {
    var current by remember { mutableStateOf(Orientation(0f, 0f, 0f)) }
    val smoothedYaw = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedRoll = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedPitch = remember { SmoothedValue(alpha = 0.1f) }

    // Body references: started by the initial 3-reading capture, extended
    // by a refread right before each wheel on that side.
    var leftBaseReadings by remember { mutableStateOf(listOf<Float>()) }
    var rightBaseReadings by remember { mutableStateOf(listOf<Float>()) }

    // 3 readings per wheel, averaged
    // together to filter out sensor noise before any toe math.
    var rearLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var rearRightReadings by remember { mutableStateOf(listOf<Float>()) }
    var frontLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var frontRightReadings by remember { mutableStateOf(listOf<Float>()) }

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

    // Each reference / wheel heading is the average of its captured readings.
    val leftBaseYaw: Float? =
        if (leftBaseReadings.isEmpty()) null else averageHeadings(leftBaseReadings)
    val rightBaseYaw: Float? =
        if (rightBaseReadings.isEmpty()) null else averageHeadings(rightBaseReadings)
    val rearLeftYaw: Float? =
        if (rearLeftReadings.isEmpty()) null else averageHeadings(rearLeftReadings)
    val rearRightYaw: Float? =
        if (rearRightReadings.isEmpty()) null else averageHeadings(rearRightReadings)
    val frontLeftYaw: Float? =
        if (frontLeftReadings.isEmpty()) null else averageHeadings(frontLeftReadings)
    val frontRightYaw: Float? =
        if (frontRightReadings.isEmpty()) null else averageHeadings(frontRightReadings)

    // Individual toe, each measured against the body reference on the SAME
    // side, with auto-correct for a possible 180° phone flip between the
    // base and wheel readings (a flipped reading shows up as |toe| > 90°,
    // which real toe can never be).
    val frontLeftToe: Float? =
        if (frontLeftYaw != null && leftBaseYaw != null)
            flipCorrect(normalizeAngle(frontLeftYaw - leftBaseYaw))
        else null
    val frontRightToe: Float? =
        if (frontRightYaw != null && rightBaseYaw != null)
            flipCorrect(normalizeAngle(rightBaseYaw - frontRightYaw))
        else null
    val rearLeftToe: Float? =
        if (rearLeftYaw != null && leftBaseYaw != null)
            flipCorrect(normalizeAngle(rearLeftYaw - leftBaseYaw))
        else null
    val rearRightToe: Float? =
        if (rearRightYaw != null && rightBaseYaw != null)
            flipCorrect(normalizeAngle(rightBaseYaw - rearRightYaw))
        else null

    // PRIMARY RESULT -- total toe per axle from the DIRECT left-vs-right
    // difference on that axle. No body reference involved, so common-mode
    // magnetic distortion (same hand hold, seconds apart) cancels out. This
    // is the number that reads correctly even before a base exists.
    val rearTotalToe: Float? =
        if (rearLeftYaw != null && rearRightYaw != null)
            flipCorrect(normalizeAngle(rearLeftYaw - rearRightYaw))
        else null
    val frontTotalToe: Float? =
        if (frontLeftYaw != null && frontRightYaw != null)
            flipCorrect(normalizeAngle(frontLeftYaw - frontRightYaw))
        else null

    val isLevelEnough = abs(current.pitch) < 1.5f && abs(current.roll) < 1.5f
    val currentStep = when {
        leftBaseReadings.size < 3 -> 1
        rearLeftReadings.size < 3 -> 2
        frontLeftReadings.size < 3 -> 3
        rightBaseReadings.size < 3 -> 4
        rearRightReadings.size < 3 -> 5
        frontRightReadings.size < 3 -> 6
        else -> 7
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Toe", fontWeight = FontWeight.Bold)

        val wheelMount =
            "Place the phone FLAT against the OUTER edge of the wheel rim " +
                "(not the tire), screen facing UP toward the sky, TOP of the " +
                "phone pointing toward the FRONT of the car."
        val baseMount =
            "Hold the phone FLAT against the body spot, screen facing UP " +
                "toward the sky, TOP of the phone pointing toward the FRONT " +
                "of the car."
        val nextWheelHint = " Tap \"Capture\" 3 times -- the app averages " +
            "them and moves on automatically."

        val instructions = when (currentStep) {
            1 -> "\nStep 1 of 6: Set the LEFT body reference.\n\n" +
                "Find a flat, repeatable spot on the car body on the LEFT " +
                "side -- the pinch-weld seam under the rocker panel is the " +
                "best choice on almost any car. You'll use the SAME spot " +
                "for every left-side reading.\n\n" +
                baseMount + nextWheelHint + "\n\nEvery left wheel is later " +
                "compared to this body spot, which cancels the magnetic " +
                "interference the car's steel causes.\n\nYou'll finish the " +
                "whole LEFT side (steps 1-3) before switching to the right."
            2 -> "\nStep 2 of 6: REAR-LEFT wheel.\n\n" +
                "1) " + baseMount + " at the LEFT body spot, then tap " +
                "\"Refresh Left Base\".\n" +
                "2) RIGHT AWAY (within about 20 seconds), move to the " +
                "REAR-LEFT wheel. " + wheelMount + " and tap \"Capture " +
                "Rear Left\" 3 times.\n\nReading the body spot and the " +
                "wheel back-to-back is what makes the compass error cancel."
            3 -> "\nStep 3 of 6: FRONT-LEFT wheel.\n\n" +
                "1) " + baseMount + " at the LEFT body spot, then tap " +
                "\"Refresh Left Base\".\n" +
                "2) RIGHT AWAY (within about 20 seconds), move to the " +
                "FRONT-LEFT wheel. " + wheelMount + " and tap \"Capture " +
                "Front Left\" 3 times.\n\nThat's the left side done."
            4 -> "\nStep 4 of 6: Set the RIGHT body reference.\n\n" +
                "Now walk around to the RIGHT side and find the matching " +
                "spot -- the pinch-weld seam under the right rocker panel. " +
                "Use the SAME spot for every right-side reading.\n\n" +
                baseMount + nextWheelHint
            5 -> "\nStep 5 of 6: REAR-RIGHT wheel.\n\n" +
                "1) " + baseMount + " at the RIGHT body spot, then tap " +
                "\"Refresh Right Base\".\n" +
                "2) RIGHT AWAY (within about 20 seconds), move to the " +
                "REAR-RIGHT wheel. " + wheelMount + " and tap \"Capture " +
                "Rear Right\" 3 times."
            6 -> "\nStep 6 of 6: FRONT-RIGHT wheel.\n\n" +
                "1) " + baseMount + " at the RIGHT body spot, then tap " +
                "\"Refresh Right Base\".\n" +
                "2) RIGHT AWAY (within about 20 seconds), move to the " +
                "FRONT-RIGHT wheel. " + wheelMount + " and tap \"Capture " +
                "Front Right\" 3 times."
            else -> "\nDone! Results are below. Tap Restart to measure " +
                "again."
        }
        Text(instructions, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Text(
            text = "\n%.2f°".format(current.yaw),
            color = if (isLevelEnough) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        Text(if (isLevelEnough) "Steady" else "Keep the phone level and flat -- screen facing UP toward the sky, top toward the front -- until this turns green")

        Button(
            onClick = { leftBaseReadings = leftBaseReadings + current.yaw },
            enabled = currentStep == 1
        ) {
            Text("Capture Left Base (${leftBaseReadings.size}/3)")
        }
        Button(
            onClick = { leftBaseReadings = leftBaseReadings + current.yaw },
            enabled = currentStep == 2 || currentStep == 3
        ) {
            Text("Refresh Left Base")
        }
        Button(
            onClick = { rearLeftReadings = rearLeftReadings + current.yaw },
            enabled = currentStep == 2
        ) {
            Text("Capture Rear Left (${rearLeftReadings.size}/3)")
        }
        Button(
            onClick = { frontLeftReadings = frontLeftReadings + current.yaw },
            enabled = currentStep == 3
        ) {
            Text("Capture Front Left (${frontLeftReadings.size}/3)")
        }
        Button(
            onClick = { rightBaseReadings = rightBaseReadings + current.yaw },
            enabled = currentStep == 4
        ) {
            Text("Capture Right Base (${rightBaseReadings.size}/3)")
        }
        Button(
            onClick = { rightBaseReadings = rightBaseReadings + current.yaw },
            enabled = currentStep == 5 || currentStep == 6
        ) {
            Text("Refresh Right Base")
        }
        Button(
            onClick = { rearRightReadings = rearRightReadings + current.yaw },
            enabled = currentStep == 5
        ) {
            Text("Capture Rear Right (${rearRightReadings.size}/3)")
        }
        Button(
            onClick = { frontRightReadings = frontRightReadings + current.yaw },
            enabled = currentStep == 6
        ) {
            Text("Capture Front Right (${frontRightReadings.size}/3)")
        }

        frontTotalToe?.let { Text("\nFRONT TOTAL TOE: %.2f°  (most reliable)".format(it)) }
        rearTotalToe?.let { Text("REAR TOTAL TOE: %.2f°  (most reliable)".format(it)) }
        frontLeftToe?.let { Text("Front-left individual toe: %.2f°".format(it)) }
        frontRightToe?.let { Text("Front-right individual toe: %.2f°".format(it)) }
        rearLeftToe?.let { Text("Rear-left individual toe: %.2f°".format(it)) }
        rearRightToe?.let { Text("Rear-right individual toe: %.2f°".format(it)) }

        if (currentStep == 7) {
            // Visual: each tire rotated by its individual toe (exaggerated),
            // a top-down view with the front of the car pointing UP.
            Text(
                text = "\nTop-down view -- front of the car points UP. " +
                    "Each tire is rotated by that wheel's individual toe, " +
                    "exaggerated ${ToeVisualExaggeration.toInt()}x so small " +
                    "angles are visible.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ToeTireVisual(
                    toe = frontLeftToe,
                    label = "Front-left",
                    rotationDeg = clampVisualRotation(
                        (frontLeftToe ?: 0f) * ToeVisualExaggeration
                    )
                )
                ToeTireVisual(
                    toe = frontRightToe,
                    label = "Front-right",
                    rotationDeg = clampVisualRotation(
                        -(frontRightToe ?: 0f) * ToeVisualExaggeration
                    )
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ToeTireVisual(
                    toe = rearLeftToe,
                    label = "Rear-left",
                    rotationDeg = clampVisualRotation(
                        (rearLeftToe ?: 0f) * ToeVisualExaggeration
                    )
                )
                ToeTireVisual(
                    toe = rearRightToe,
                    label = "Rear-right",
                    rotationDeg = clampVisualRotation(
                        -(rearRightToe ?: 0f) * ToeVisualExaggeration
                    )
                )
            }

            val anyImplausible = listOfNotNull(
                frontTotalToe, rearTotalToe
            ).any { abs(it) > 10f }

            if (anyImplausible) {
                Text(
                    text = "\nThe TOTAL numbers are still far larger than a " +
                        "real toe reading (actual toe is under about 2°). " +
                        "This is the direct left-vs-right difference, which " +
                        "shouldn't happen if the car is parked on level ground " +
                        "and both wheels were read the same way. Either one " +
                        "wheel's reading was taken with the phone flipped " +
                        "AROUND (top pointing toward the rear), or the level " +
                        "surface / steering-center preflight isn't actually " +
                        "satisfied. Restart and double-check the phone's " +
                        "direction on every wheel.",
                    textAlign = TextAlign.Center,
                    color = Color(0xFFC62828),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            } else {
                Text(
                    text = "\nThese totals come from the direct difference " +
                        "between the two wheels on each axle, so magnetic " +
                        "interference mostly cancels out. The individual " +
                        "wheel numbers below depend on a body reference and " +
                        "are more sensitive to the phone's direction -- treat " +
                        "those as diagnostic.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Text(
                text = "\nWhat this means: a positive number means that " +
                    "wheel (or axle) points slightly toward the centerline " +
                    "of the car (toe-in). A negative number means it " +
                    "points slightly away (toe-out).",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )

            frontTotalToe?.let {
                Text(
                    text = frontToeGuidance(it),
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            val frontSplitUneven = frontLeftToe != null &&
                frontRightToe != null &&
                abs(frontLeftToe - frontRightToe) > 0.3f
            if (frontSplitUneven) {
                Text(
                    text = "\nThe left and right front readings differ " +
                        "quite a bit from each other. That's often more " +
                        "worth investigating than the total toe number " +
                        "itself -- it can point to a bent part, uneven " +
                        "ride height, or a steering wheel that wasn't " +
                        "actually centered during measurement.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Text(
                text = "\nThis is a ballpark tool, not a substitute for a " +
                    "professional alignment rack. If a number looks way " +
                    "off, have it double-checked by a shop before making " +
                    "any adjustments.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Button(onClick = {
            leftBaseReadings = emptyList()
            rightBaseReadings = emptyList()
            rearLeftReadings = emptyList()
            rearRightReadings = emptyList()
            frontLeftReadings = emptyList()
            frontRightReadings = emptyList()
        }) {
            Text("\nRestart")
        }
    }
}

/**
 * How much to exaggerate the tire rotation in the results visual. Real toe
 * is under ~2°, which is invisible to the eye, so the visual multiplies the
 * angle by this. The number label next to each tire always shows the REAL
 * (unexaggerated) value.
 */
private const val ToeVisualExaggeration = 15f

/**
 * If the tire PNG's "forward" direction doesn't point UP in the raw image,
 * set this so the graphic sits upright: 0 = already upright, 90 = forward
 * points right, 180 = forward points down, 270 = forward points left.
 */
private const val ToeVisualBaseRotation = 0f

/**
 * Clamp the visual rotation so a garbage reading (or a big toe error)
 * doesn't spin the tire graphic around like a propeller -- beyond ±45° of
 * visible tilt the number is the useful information anyway.
 */
private fun clampVisualRotation(degrees: Float): Float =
    degrees.coerceIn(-45f, 45f)

/**
 * One tire graphic, rotated by a wheel's individual toe to visualize which
 * way that wheel points. This is a TOP-DOWN view: the front of the car
 * points UP the screen, so:
 *   LEFT wheels:  toe-in (+) tilts the tire's top toward the centerline
 *                 (clockwise)          -> rotationDeg =  +toe * exaggeration
 *   RIGHT wheels: toe-in (+) tilts the top toward the centerline
 *                 (counter-clockwise)  -> rotationDeg =  -toe * exaggeration
 */
@Composable
private fun ToeTireVisual(
    toe: Float?,
    label: String,
    rotationDeg: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.tire),
            contentDescription = "$label tire, tilted to show toe direction",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 44.dp, height = 110.dp)
                .graphicsLayer {
                    rotationZ = ToeVisualBaseRotation + rotationDeg
                }
        )
        Text(label)
        toe?.let { Text("%.2f°".format(it), fontSize = 12.sp) }
    }
}

/**
 * Wrap an angle into (-180, 180]. Toe is a tiny angular difference around
 * a shared heading, so normalizing before comparing protects against the
 * ±180° wrap when the car is parked nose-on to some compass direction.
 */
private fun normalizeAngle(angle: Float): Float {
    var a = angle % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

/**
 * Wraparound-safe average of any number of headings using vector
 * summation, so readings near the ±180° wrap don't average to garbage.
 */
private fun averageHeadings(angles: List<Float>): Float {
    if (angles.isEmpty()) return 0f
    var sinSum = 0f
    var cosSum = 0f
    for (a in angles) {
        val rad = Math.toRadians(a.toDouble())
        sinSum += sin(rad.toFloat())
        cosSum += cos(rad.toFloat())
    }
    return Math.toDegrees(atan2(sinSum, cosSum).toDouble()).toFloat()
}

/**
 * Correct a toe value that lands farther than ±90° from zero -- something
 * real alignment can never produce. That signature means the phone was
 * flipped ~180° between the two readings it compares (base vs wheel, or
 * left vs right wheel), so we rotate the reading back by 180°.
 */
private fun flipCorrect(toe: Float): Float =
    if (abs(toe) > 90f) normalizeAngle(toe - 180f * sign(toe)) else toe

/**
 * Rough, non-vehicle-specific guidance for FRONT total toe. Real specs vary
 * by vehicle -- some target a hair of toe-in, some toe-out -- so treat this
 * as "does this look unusual," not a precise target.
 */
private fun frontToeGuidance(totalToeDegrees: Float): String = when {
    abs(totalToeDegrees) <= 0.15f ->
        "This is close to zero, which is roughly what a lot of passenger " +
            "cars target -- probably fine as a ballpark check."

    totalToeDegrees < -0.15f ->
        "This reads as toe-out (front of the wheels pointing slightly " +
            "away from each other) more than a typical passenger-car " +
            "target. Noticeable toe-out can make the car feel less " +
            "stable in a straight line and wear the outer edges of the " +
            "front tires. Toe is usually adjusted at the tie rod ends: " +
            "loosening the locknut and turning the adjuster sleeve " +
            "changes the tie rod's effective length, which changes toe. " +
            "Which direction to turn depends on your specific car's " +
            "thread direction and layout, so check a repair manual for " +
            "your vehicle or have a shop make the actual adjustment."

    else -> // totalToeDegrees > 0.15f
        "This reads as more toe-in (front of the wheels pointing slightly " +
            "toward each other) than a typical passenger-car target. " +
            "Noticeable toe-in can wear the inner edges of the front " +
            "tires. Toe is usually adjusted at the tie rod ends: loosening " +
            "the locknut and turning the adjuster sleeve changes the tie " +
            "rod's effective length, which changes toe. Which direction to " +
            "turn depends on your specific car's thread direction and " +
            "layout, so check a repair manual for your vehicle or have a " +
            "shop make the actual adjustment."
}