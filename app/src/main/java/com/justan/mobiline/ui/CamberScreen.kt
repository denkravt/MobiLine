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
import com.justan.mobiline.sensors.CalibrationOffset
import com.justan.mobiline.sensors.Orientation
import com.justan.mobiline.sensors.OrientationRepository
import com.justan.mobiline.sensors.SmoothedValue
import com.justan.mobiline.sensors.applyOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Camber in the same tire-as-control style as ToeScreen, but looking at the
 * car HEAD-ON (front and rear axles stacked, front on top). Camber is read
 * from the phone's ROLL against gravity, so unlike toe it needs NO body
 * reference -- each wheel's reading stands on its own.
 *
 * INTERACTION: each tire is a capture control -- tap it to add one reading
 * toward that wheel's average (3 taps, then it locks in; tap again to
 * re-capture/refresh). Hold the phone flat against the outer rim edge,
 * screen facing you, upright, and keep it steady while you tap.
 *
 * RESULT: the per-wheel camber is the primary number. The cross-axle
 * SPLIT (left minus right on the same axle) is shown in the center -- a
 * large split is often more worth investigating than either number alone
 * (uneven ride height, a bent part, or a wheel that wasn't level during
 * capture).
 *
 * Sign convention: negative = the top of the wheel leans IN toward the
 * car; positive = leans OUT away from it.
 */
@Composable
fun CamberScreen(
    repository: OrientationRepository,
    calibrationOffset: CalibrationOffset
) {
    var current by remember { mutableStateOf(Orientation(0f, 0f, 0f)) }
    val smoothedRoll = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedPitch = remember { SmoothedValue(alpha = 0.1f) }
    val smoothedYaw = remember { SmoothedValue(alpha = 0.1f) }

    // Per-wheel 3-tap reading sets, averaged on capture (mirrors ToeScreen).
    var frontLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var frontRightReadings by remember { mutableStateOf(listOf<Float>()) }
    var rearLeftReadings by remember { mutableStateOf(listOf<Float>()) }
    var rearRightReadings by remember { mutableStateOf(listOf<Float>()) }

    // Snapshot camber per wheel at the moment its 3rd tap lands.
    var frontLeftCamber by remember { mutableStateOf<Float?>(null) }
    var frontRightCamber by remember { mutableStateOf<Float?>(null) }
    var rearLeftCamber by remember { mutableStateOf<Float?>(null) }
    var rearRightCamber by remember { mutableStateOf<Float?>(null) }

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

    val liveCamber = current.roll
    // Level enough = the phone isn't tipped forward/back, which would throw
    // off the roll reading.
    val isLevelEnough = abs(current.pitch) < 1.5f

    fun tapReadings(readings: List<Float>): List<Float> =
        if (readings.size >= 3) listOf(liveCamber) else readings + liveCamber

    fun onWheelTap(
        readings: List<Float>,
        setReadings: (List<Float>) -> Unit,
        setCamber: (Float?) -> Unit
    ) {
        val next = tapReadings(readings)
        setReadings(next)
        setCamber(if (next.size == 3) averageAngles(next) else null)
    }

    val frontLeft: Float? = if (frontLeftReadings.size == 3) frontLeftCamber else null
    val frontRight: Float? = if (frontRightReadings.size == 3) frontRightCamber else null
    val rearLeft: Float? = if (rearLeftReadings.size == 3) rearLeftCamber else null
    val rearRight: Float? = if (rearRightReadings.size == 3) rearRightCamber else null

    val frontSplit: Float? =
        if (frontLeft != null && frontRight != null) frontLeft - frontRight else null
    val rearSplit: Float? =
        if (rearLeft != null && rearRight != null) rearLeft - rearRight else null

    val hasResults = listOfNotNull(frontLeft, frontRight, rearLeft, rearRight).isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Camber", fontWeight = FontWeight.Bold)
            TextButton(onClick = { showInfo = !showInfo }) {
                Text(if (showInfo) "\u2715 Info" else "\u2630 Info")
            }
        }

        if (showInfo) {
            Text(
                text = "\nTap a tire to capture that wheel's camber (3 taps, " +
                    "averaged -- tap again after to re-capture it). Hold the " +
                    "phone FLAT against the outer rim edge (the metal lip, not " +
                    "the tire), screen facing you and upright, and keep it " +
                    "steady during each tap.\n\nThis is a head-on view of the " +
                    "car -- front axle on top, rear below. Each tire leans to " +
                    "show that wheel's camber, exaggerated so small angles are " +
                    "visible; the number by each tire is the real value.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "\n%.2f°".format(liveCamber),
            color = if (isLevelEnough) MobGreen else Color(0xFFC62828)
        )
        Text(
            if (isLevelEnough)
                "Steady -- tap a tire to capture it"
            else
                "Keep the phone upright (not tipped forward/back) until this turns green"
        )

        // FRONT axle (top), REAR axle (bottom).
        CamberAxle(
            label = "FRONT",
            leftLabel = "Front-left",
            rightLabel = "Front-right",
            leftReadings = frontLeftReadings,
            rightReadings = frontRightReadings,
            leftCamber = frontLeftCamber,
            rightCamber = frontRightCamber,
            split = frontSplit,
            splitLabel = "Split",
            onLeftTap = { onWheelTap(frontLeftReadings, { frontLeftReadings = it }, { frontLeftCamber = it }) },
            onRightTap = { onWheelTap(frontRightReadings, { frontRightReadings = it }, { frontRightCamber = it }) }
        )

        CamberAxle(
            label = "REAR",
            leftLabel = "Rear-left",
            rightLabel = "Rear-right",
            leftReadings = rearLeftReadings,
            rightReadings = rearRightReadings,
            leftCamber = rearLeftCamber,
            rightCamber = rearRightCamber,
            split = rearSplit,
            splitLabel = "Split",
            onLeftTap = { onWheelTap(rearLeftReadings, { rearLeftReadings = it }, { rearLeftCamber = it }) },
            onRightTap = { onWheelTap(rearRightReadings, { rearRightReadings = it }, { rearRightCamber = it }) }
        )

        if (hasResults) {
            TextButton(onClick = { showGuidance = !showGuidance }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (showGuidance) "Hide guidance \u25B4" else "Show guidance \u25BE")
            }
        }

        if (hasResults && showGuidance) {
            Text(
                text = "\nHead-on view -- front axle on top, rear on the " +
                    "bottom, car pointing away from you. Each tire leans by " +
                    "that wheel's camber, exaggerated " +
                    "${CamberVisualExaggeration.toInt()}x so small angles are " +
                    "visible. A negative camber (top of the wheel leaning " +
                    "IN toward the car) tips the tire toward the center " +
                    "line; positive tips it out.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            val frontSplitUneven = frontLeft != null && frontRight != null &&
                abs(frontLeft - frontRight) > 0.75f
            val rearSplitUneven = rearLeft != null && rearRight != null &&
                abs(rearLeft - rearRight) > 0.75f

            if (frontSplitUneven) {
                Text(
                    text = "\nThe FRONT left and right wheels read quite " +
                        "differently from each other (split over 0.75°). That's " +
                        "often more worth investigating than either number " +
                        "alone -- it can point to uneven ride height, a bent " +
                        "or loose part, or a wheel that wasn't level during " +
                        "capture.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            if (rearSplitUneven) {
                Text(
                    text = "\nThe REAR left and right wheels read quite " +
                        "differently from each other (split over 0.75°). Same " +
                        "story as the front -- uneven ride height, a bent " +
                        "part, or a leveling issue during capture are the " +
                        "usual suspects.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Text(
                            text = "\nThe per-wheel numbers are the primary result; the " +
                                "Split in the middle is the left-minus-right difference " +
                                "on that axle. Negative camber (top leaning in) is " +
                                "normal in small amounts; noticeably different from the " +
                                "other side is the thing to investigate.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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

        Button(onClick = {
            frontLeftReadings = emptyList()
            frontRightReadings = emptyList()
            rearLeftReadings = emptyList()
            rearRightReadings = emptyList()
            frontLeftCamber = null
            frontRightCamber = null
            rearLeftCamber = null
            rearRightCamber = null
        }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Restart")
        }
    }
}

/** How much to exaggerate the tire lean in the visual. */
private const val CamberVisualExaggeration = 25f

/** The tire graphic's base orientation for a head-on wheel. */
private const val CamberVisualBaseRotation = 0f

/** One axle row: two tire capture controls with the split in the middle. */
@Composable
private fun CamberAxle(
    label: String,
    leftLabel: String,
    rightLabel: String,
    leftReadings: List<Float>,
    rightReadings: List<Float>,
    leftCamber: Float?,
    rightCamber: Float?,
    split: Float?,
    splitLabel: String,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CamberTireCapture(
            label = leftLabel,
            readings = leftReadings,
            value = leftCamber,
            onTap = onLeftTap,
            rotationDeg = clampVisualRotation(-(leftCamber ?: 0f) * CamberVisualExaggeration)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                text = split?.let { "%.2f°".format(it) } ?: "---",
                fontWeight = FontWeight.Bold
            )
            Text(splitLabel, textAlign = TextAlign.Center, fontSize = 11.sp)
        }

        CamberTireCapture(
            label = rightLabel,
            readings = rightReadings,
            value = rightCamber,
            onTap = onRightTap,
            rotationDeg = clampVisualRotation((rightCamber ?: 0f) * CamberVisualExaggeration)
        )
    }
}

/** One tire graphic that IS the capture control (head-on view). */
@Composable
private fun CamberTireCapture(
    label: String,
    readings: List<Float>,
    value: Float?,
    onTap: () -> Unit,
    rotationDeg: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onTap).padding(4.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.tire),
            contentDescription = "$label tire -- tap to capture camber",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 44.dp, height = 110.dp)
                .graphicsLayer {
                    rotationZ = if (value != null) CamberVisualBaseRotation + rotationDeg else CamberVisualBaseRotation
                }
        )
        Text(label, fontSize = 12.sp)
        Text(
            text = when {
                readings.isEmpty() -> "Tap to capture"
                readings.size < 3 -> "${readings.size}/3 -- tap to continue"
                else -> "%.2f°  (tap to redo)".format(value ?: 0f)
            },
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

private fun clampVisualRotation(degrees: Float): Float =
    degrees.coerceIn(-45f, 45f)

/** Wraparound-safe average of a set of angles via vector summation. */
internal fun averageAngles(angles: List<Float>): Float {
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