package com.justan.mobiline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justan.mobiline.R
import com.justan.mobiline.data.SavedMeasurement
import com.justan.mobiline.data.SessionStore
import com.justan.mobiline.sensors.CalibrationOffset
import com.justan.mobiline.sensors.Orientation
import com.justan.mobiline.sensors.OrientationRepository
import com.justan.mobiline.sensors.SmoothedValue
import com.justan.mobiline.sensors.applyOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Toe uses yaw (heading) instead of roll, measured BODY-ALIGN style.
 *
 * WHY BODY REFERENCES:
 * The phone's compass heading is only as good as the magnetic field around
 * it, and a car's steel bends that field -- differently at each corner.
 * The fix is to measure every wheel against a FIXED BODY REFERENCE SURFACE
 * captured on the SAME side moments before the wheel. The body spot and
 * the wheel are read back-to-back, so the magnetic distortion -- roughly
 * constant across that small area and short time -- is present in BOTH
 * readings and cancels when you subtract them.
 *
 * IMPORTANT: the reference SPOT itself doesn't move -- what drifts is the
 * phone's *reading* of it (ambient interference shifts slightly as you
 * move around the car, plus ordinary sensor noise/drift). Refreshing the
 * base right before a wheel just keeps the two readings close enough in
 * time that whatever drift exists is common to both and cancels out.
 *
 * SNAPSHOTTING (important for correctness): each wheel's individual toe is
 * computed from -- and locked to -- whatever the base was AT THE MOMENT
 * that wheel finished its 3rd tap. Refreshing a base afterward (e.g. to
 * capture the OTHER wheel on the same side) never changes or invalidates
 * an already-captured wheel's number, since that number isn't recomputed
 * against the live base -- it's frozen at capture time. Freshness (see
 * [isPairingFresh]) is likewise decided once, at that same moment.
 *
 * INTERACTION MODEL (tap-based, no forced order):
 * Each tire on screen is itself the capture control -- tap a tire to add
 * one reading toward that wheel's average (3 taps, then it locks in; tap
 * again to re-capture/refresh it, which also re-snapshots its base pairing
 * from scratch). A small base button sits between the two LEFT tires and
 * another between the two RIGHT tires. There's no requirement to start on
 * the left or the right -- the PRIMARY result (total toe per axle) only
 * needs both wheels on that axle captured, in any order, and doesn't need
 * a base at all.
 *
 * MATH:
 *   PRIMARY -- TOTAL toe per axle is the DIRECT left-vs-right difference
 *   between the two wheels on that axle. No body reference is involved, so
 *   whatever magnetic distortion the car causes is roughly common to both
 *   readings (same hand hold, seconds apart) and cancels out.
 *
 *   DIAGNOSTIC -- individual wheel toe uses the same-side body reference
 *   as it stood at capture time (see snapshotting above), flip-corrected
 *   for a possible 180° phone flip.
 *
 * Sign convention: positive = toe-in, negative = toe-out.
 */
@Composable
fun ToeScreen(
    repository: OrientationRepository,
    calibrationOffset: CalibrationOffset,
    store: SessionStore,
    onBack: () -> Unit
) {
    var current by remember { mutableStateOf(Orientation(0f, 0f, 0f)) }
    val smoothedYaw = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedRoll = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedPitch = remember { SmoothedValue(alpha = 0.1f) }

    // Body references: each tap adds a reading (up to 3); tapping again
    // once full starts a fresh capture (a "refresh"). *CompletedAt records
    // the wall-clock time the 3rd reading landed.
    var leftBaseReadings by remember { mutableStateOf(listOf<Float>()) }
    var rightBaseReadings by remember { mutableStateOf(listOf<Float>()) }
    var leftBaseCompletedAt by remember { mutableStateOf<Long?>(null) }
    var rightBaseCompletedAt by remember { mutableStateOf<Long?>(null) }

    // 3 tap-readings per wheel, averaged together to filter out sensor
    // noise -- used for this wheel's own heading (feeds total toe, which
    // never depends on a base).
    var rearLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var rearRightReadings by remember { mutableStateOf(listOf<Float>()) }
    var frontLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var frontRightReadings by remember { mutableStateOf(listOf<Float>()) }

    // Individual toe + freshness, SNAPSHOTTED at the moment each wheel's
    // 3rd tap lands -- these do not get recomputed when a base is later
    // refreshed. Cleared back to null/false as soon as that wheel starts
    // a fresh re-capture, until it completes again.
    var frontLeftToe by remember { mutableStateOf<Float?>(null) }
    var frontRightToe by remember { mutableStateOf<Float?>(null) }
    var rearLeftToe by remember { mutableStateOf<Float?>(null) }
    var rearRightToe by remember { mutableStateOf<Float?>(null) }
    var frontLeftFresh by remember { mutableStateOf(false) }
    var frontRightFresh by remember { mutableStateOf(false) }
    var rearLeftFresh by remember { mutableStateOf(false) }
    var rearRightFresh by remember { mutableStateOf(false) }

    var showInfo by remember { mutableStateOf(false) }
    var showGuidance by remember { mutableStateOf(false) }

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

    // Wheel headings (used only for the PRIMARY total-toe result, which
    // never depends on a base and is unaffected by anything below).
    val rearLeftYaw: Float? =
        if (rearLeftReadings.isEmpty()) null else averageHeadings(rearLeftReadings)
    val rearRightYaw: Float? =
        if (rearRightReadings.isEmpty()) null else averageHeadings(rearRightReadings)
    val frontLeftYaw: Float? =
        if (frontLeftReadings.isEmpty()) null else averageHeadings(frontLeftReadings)
    val frontRightYaw: Float? =
        if (frontRightReadings.isEmpty()) null else averageHeadings(frontRightReadings)

    // PRIMARY RESULT -- total toe per axle from the DIRECT left-vs-right
    // difference on that axle. No body reference involved, so common-mode
    // magnetic distortion cancels out regardless of any base state.
    val rearTotalToe: Float? =
        if (rearLeftYaw != null && rearRightYaw != null)
            flipCorrect(normalizeAngle(rearLeftYaw - rearRightYaw))
        else null
    val frontTotalToe: Float? =
        if (frontLeftYaw != null && frontRightYaw != null)
            flipCorrect(normalizeAngle(frontLeftYaw - frontRightYaw))
        else null

    val isLevelEnough = abs(current.pitch) < 1.5f && abs(current.roll) < 1.5f

    fun tapReading(readings: List<Float>): List<Float> =
        if (readings.size >= 3) listOf(current.yaw) else readings + current.yaw

    // Handles one tap on a WHEEL tire. On completion (3rd reading), this
    // snapshots the base -- as it stands right now -- into this wheel's
    // toe + freshness, permanently (until the wheel is re-captured).
    // Later base refreshes cannot touch this snapshot.
    fun onWheelTap(
        readings: List<Float>,
        setReadings: (List<Float>) -> Unit,
        baseReadings: List<Float>,
        baseCompletedAt: Long?,
        setToe: (Float?) -> Unit,
        setFresh: (Boolean) -> Unit,
        toeFrom: (wheelYaw: Float, baseYaw: Float) -> Float
    ) {
        val next = tapReading(readings)
        setReadings(next)
        if (next.size == 3) {
            val wheelYaw = averageHeadings(next)
            val baseYaw = if (baseReadings.isEmpty()) null else averageHeadings(baseReadings)
            if (baseYaw != null) {
                setToe(flipCorrect(normalizeAngle(toeFrom(wheelYaw, baseYaw))))
                setFresh(isPairingFresh(baseCompletedAt, System.currentTimeMillis()))
            } else {
                setToe(null)
                setFresh(false)
            }
        } else {
            // Mid-recapture -- clear the old snapshot until this wheel
            // completes again.
            setToe(null)
            setFresh(false)
        }
    }

    fun onBaseTap(readings: List<Float>, setReadings: (List<Float>) -> Unit, setCompletedAt: (Long?) -> Unit) {
        val next = tapReading(readings)
        setReadings(next)
        setCompletedAt(if (next.size == 3) System.currentTimeMillis() else null)
    }

    val anyImplausible = listOfNotNull(frontTotalToe, rearTotalToe).any { abs(it) > 10f }
    val hasResults = frontTotalToe != null || rearTotalToe != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BackButton(onBack = onBack)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Toe", fontWeight = FontWeight.Bold)
            TextButton(onClick = { showInfo = !showInfo }) {
                Text(if (showInfo) "\u2715 Info" else "\u2630 Info")
            }
        }

        if (showInfo) {
            Text(
                text = "\nTap a tire to capture that wheel (3 taps, " +
                    "averaged -- tap again after to re-capture it). Hold " +
                    "the phone FLAT against the outer rim edge, screen " +
                    "facing UP toward the sky, TOP of the phone pointing " +
                    "toward the FRONT of the car.\n\nUse the small base " +
                    "button between the left tires (or the right tires) " +
                    "to capture/refresh that side's body reference right " +
                    "before tapping a tire on that side -- you can start " +
                    "on whichever side of the car you're standing at. The " +
                    "reference spot itself doesn't move; refreshing just " +
                    "keeps the base and wheel readings close in time so " +
                    "ambient magnetic drift cancels out.\n\nA body " +
                    "reference is a flat, repeatable spot on the car -- " +
                    "the pinch-weld seam under the rocker panel works on " +
                    "almost any car. Use the same spot every time on that " +
                    "side.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (repository.usesMagnetometerForHeading()) {
            Text(
                text = "\nThis device has no gyroscope, so headings fall " +
                    "back to the compass. Expect more noise/instability in " +
                    "readings taken close to the car's steel body than on " +
                    "a device with a gyroscope.",
                textAlign = TextAlign.Center,
                color = Color(0xFFC62828),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "\n%.2f°".format(current.yaw),
            color = if (isLevelEnough) MobGreen else Color(0xFFC62828)
        )
        Text(
            if (isLevelEnough) "Steady -- tap a tire or base button to capture"
            else "Keep the phone level and flat until this turns green"
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // LEFT column: front-left tire, left base button, rear-left tire.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ToeTireCapture(
                    label = "Front-left",
                    readings = frontLeftReadings,
                    toe = frontLeftToe,
                    fresh = frontLeftFresh,
                    onTap = {
                        onWheelTap(
                            frontLeftReadings, { frontLeftReadings = it },
                            leftBaseReadings, leftBaseCompletedAt,
                            { frontLeftToe = it }, { frontLeftFresh = it },
                            { wheelYaw, baseYaw -> wheelYaw - baseYaw }
                        )
                    },
                    rotationDeg = clampVisualRotation((frontLeftToe ?: 0f) * ToeVisualExaggeration)
                )
                BaseCaptureButton(
                    label = "Left Base",
                    readings = leftBaseReadings,
                    onTap = { onBaseTap(leftBaseReadings, { leftBaseReadings = it }, { leftBaseCompletedAt = it }) }
                )
                ToeTireCapture(
                    label = "Rear-left",
                    readings = rearLeftReadings,
                    toe = rearLeftToe,
                    fresh = rearLeftFresh,
                    onTap = {
                        onWheelTap(
                            rearLeftReadings, { rearLeftReadings = it },
                            leftBaseReadings, leftBaseCompletedAt,
                            { rearLeftToe = it }, { rearLeftFresh = it },
                            { wheelYaw, baseYaw -> wheelYaw - baseYaw }
                        )
                    },
                    rotationDeg = clampVisualRotation((rearLeftToe ?: 0f) * ToeVisualExaggeration)
                )
            }

            // CENTER column: the two total-toe results.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(110.dp)
            ) {
                Text(
                    text = frontTotalToe?.let { "%.2f°".format(it) } ?: "---",
                    fontWeight = FontWeight.Bold
                )
                Text("Front\nTotal Toe", textAlign = TextAlign.Center, fontSize = 12.sp)
                Text(
                    text = "\n" + (rearTotalToe?.let { "%.2f°".format(it) } ?: "---"),
                    fontWeight = FontWeight.Bold
                )
                Text("Rear\nTotal Toe", textAlign = TextAlign.Center, fontSize = 12.sp)
            }

            // RIGHT column: front-right tire, right base button, rear-right tire.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ToeTireCapture(
                    label = "Front-right",
                    readings = frontRightReadings,
                    toe = frontRightToe,
                    fresh = frontRightFresh,
                    onTap = {
                        onWheelTap(
                            frontRightReadings, { frontRightReadings = it },
                            rightBaseReadings, rightBaseCompletedAt,
                            { frontRightToe = it }, { frontRightFresh = it },
                            { wheelYaw, baseYaw -> baseYaw - wheelYaw }
                        )
                    },
                    rotationDeg = clampVisualRotation(-(frontRightToe ?: 0f) * ToeVisualExaggeration)
                )
                BaseCaptureButton(
                    label = "Right Base",
                    readings = rightBaseReadings,
                    onTap = { onBaseTap(rightBaseReadings, { rightBaseReadings = it }, { rightBaseCompletedAt = it }) }
                )
                ToeTireCapture(
                    label = "Rear-right",
                    readings = rearRightReadings,
                    toe = rearRightToe,
                    fresh = rearRightFresh,
                    onTap = {
                        onWheelTap(
                            rearRightReadings, { rearRightReadings = it },
                            rightBaseReadings, rightBaseCompletedAt,
                            { rearRightToe = it }, { rearRightFresh = it },
                            { wheelYaw, baseYaw -> baseYaw - wheelYaw }
                        )
                    },
                    rotationDeg = clampVisualRotation(-(rearRightToe ?: 0f) * ToeVisualExaggeration)
                )
            }
        }

        // Actionable warning -- kept visible unconditionally since it flags
        // data that's likely garbage rather than just explaining a good result.
        if (anyImplausible) {
            Text(
                text = "\nThe TOTAL numbers are far larger than a real toe " +
                    "reading. Either a wheel was read with the phone " +
                    "flipped AROUND, or the level-surface / steering-" +
                    "center preflight wasn't actually satisfied. Restart " +
                    "and double-check the phone's direction on every wheel.",
                textAlign = TextAlign.Center,
                color = Color(0xFFC62828),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (hasResults) {
            TextButton(onClick = { showGuidance = !showGuidance }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (showGuidance) "Hide guidance \u25B4" else "Show guidance \u25BE")
            }
        }

        if (hasResults && showGuidance) {
            Text(
                text = "\nTop-down view -- front of the car points UP. " +
                    "Each tire is rotated by that wheel's individual toe " +
                    "as it stood when captured, exaggerated " +
                    "${ToeVisualExaggeration.toInt()}x so small angles are " +
                    "visible. The Total Toe numbers above are the ones to " +
                    "trust and don't need a base at all.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            if (!anyImplausible) {
                Text(
                    text = "\nThese totals come from the direct difference " +
                        "between the two wheels on each axle, so magnetic " +
                        "interference mostly cancels out. The individual " +
                        "wheel numbers depend on a body reference and are " +
                        "more sensitive to the phone's direction -- treat " +
                        "those as diagnostic, and greyed-out ones mean the " +
                        "base wasn't refreshed shortly before that wheel " +
                        "was captured.",
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
                abs(frontLeftToe!! - frontRightToe!!) > 0.3f
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

        SessionSaveSection(
            store = store,
            mode = "Toe",
            enabled = frontTotalToe != null && rearTotalToe != null,
            buildMeasurements = {
                listOf(
                    SavedMeasurement("Front-left", frontLeftToe?.toDouble() ?: 0.0),
                    SavedMeasurement("Front-right", frontRightToe?.toDouble() ?: 0.0),
                    SavedMeasurement("Front total toe", frontTotalToe?.toDouble() ?: 0.0),
                    SavedMeasurement("Rear-left", rearLeftToe?.toDouble() ?: 0.0),
                    SavedMeasurement("Rear-right", rearRightToe?.toDouble() ?: 0.0),
                    SavedMeasurement("Rear total toe", rearTotalToe?.toDouble() ?: 0.0)
                )
            }
        )

        Button(onClick = {
            leftBaseReadings = emptyList()
            rightBaseReadings = emptyList()
            rearLeftReadings = emptyList()
            rearRightReadings = emptyList()
            frontLeftReadings = emptyList()
            frontRightReadings = emptyList()
            leftBaseCompletedAt = null
            rightBaseCompletedAt = null
            frontLeftToe = null
            frontRightToe = null
            rearLeftToe = null
            rearRightToe = null
            frontLeftFresh = false
            frontRightFresh = false
            rearLeftFresh = false
            rearRightFresh = false
        }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Restart")
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
 * How long after a base is refreshed it's still considered a valid pairing
 * for a wheel reading taken against it. Mirrors the original "~20 seconds"
 * rule of thumb with a small buffer. This is checked ONCE, at the moment
 * the wheel completes -- not re-checked later.
 */
private const val STALE_BASE_WINDOW_MS = 25_000L

/**
 * Whether a wheel's toe reading was a trustworthy pairing with its side's
 * body reference AT THE MOMENT IT WAS CAPTURED: the base must have existed
 * and completed at or before the wheel, within [STALE_BASE_WINDOW_MS].
 */
private fun isPairingFresh(baseCompletedAt: Long?, wheelCompletedAt: Long): Boolean {
    if (baseCompletedAt == null) return false
    val gap = wheelCompletedAt - baseCompletedAt
    return gap in 0..STALE_BASE_WINDOW_MS
}

/**
 * Clamp the visual rotation so a garbage reading (or a big toe error)
 * doesn't spin the tire graphic around like a propeller -- beyond ±45° of
 * visible tilt the number is the useful information anyway.
 */
private fun clampVisualRotation(degrees: Float): Float =
    degrees.coerceIn(-45f, 45f)

/**
 * One tire graphic that IS the capture control: tapping it adds a reading
 * (up to 3, averaged) toward this wheel's heading. This is a TOP-DOWN view:
 * the front of the car points UP the screen. `toe` and `fresh` are the
 * SNAPSHOTTED values from the moment this wheel was captured -- they don't
 * change just because a base gets refreshed afterward.
 */
@Composable
private fun ToeTireCapture(
    label: String,
    readings: List<Float>,
    toe: Float?,
    fresh: Boolean,
    onTap: () -> Unit,
    rotationDeg: Float
) {
    val showAngle = toe != null && fresh
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onTap).padding(4.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.tire),
            contentDescription = "$label tire -- tap to capture a reading",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 44.dp, height = 110.dp)
                .graphicsLayer {
                    rotationZ = if (showAngle) ToeVisualBaseRotation + rotationDeg else ToeVisualBaseRotation
                    alpha = if (toe != null && !fresh) 0.35f else 1f
                }
        )
        Text(label, fontSize = 12.sp)
        Text(
            text = when {
                readings.isEmpty() -> "Tap to capture"
                readings.size < 3 -> "${readings.size}/3 -- tap to continue"
                toe == null -> "Captured -- needs base"
                !fresh -> "%.2f°  (stale base)".format(toe)
                else -> "%.2f°  (tap to redo)".format(toe)
            },
            fontSize = 11.sp,
            color = if (toe != null && !fresh) Color(0xFFC62828) else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Small capture control for a side's body reference. A tap adds a reading
 * (up to 3, averaged); once full, tapping again refreshes it. Refreshing
 * only affects wheels captured AFTER this point -- already-captured wheels
 * on this side keep the snapshot they took at their own capture time.
 */
@Composable
private fun BaseCaptureButton(
    label: String,
    readings: List<Float>,
    onTap: () -> Unit
) {
    Button(
        onClick = onTap,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = when {
                readings.isEmpty() -> label
                readings.size < 3 -> "$label (${readings.size}/3)"
                else -> "Refresh\n$label"
            },
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
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
