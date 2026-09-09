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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.justan.mobiline.data.SavedSession
import com.justan.mobiline.data.SessionStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Lists all locally saved measurement sessions, newest first. */
@Composable
fun SavedSessionsScreen(
    store: SessionStore,
    onBack: () -> Unit
) {
    var sessions by remember { mutableStateOf<List<SavedSession>?>(null) }
    var selected by remember { mutableStateOf<SavedSession?>(null) }

    LaunchedEffect(Unit) {
        sessions = store.load().reversed()  // newest first
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BackButton(onBack = onBack)
        Text("Saved sessions", fontWeight = FontWeight.Bold, fontSize = 22.sp)

        when {
            sessions == null -> Text("\nLoading\u2026", modifier = Modifier.padding(top = 24.dp))
            sessions.isNullOrEmpty() ->
                Text(
                    "\nNo saved sessions yet. Complete a Camber or Toe " +
                        "measurement (all four wheels) and use the Save button " +
                        "in the menu.",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(sessions.orEmpty(), key = { it.id }) { session ->
                    SessionCard(session, onClick = { selected = session })
                }
            }
        }
    }

    selected?.let { session ->
        SessionDetailDialog(session = session, onDismiss = { selected = null })
    }
}

@Composable
private fun SessionCard(session: SavedSession, onClick: () -> Unit) {
    val whenStr = Instant.ofEpochMilli(session.createdAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(session.name, fontWeight = FontWeight.Bold)
        Text("${session.mode}  \u00b7  $whenStr", fontSize = 12.sp, color = Color.Gray)
        Text(
            "Tap to view",
            fontSize = 11.sp,
            color = MobGreen
        )
    }
}

/**
 * Popup showing a saved session's measurements as the same visual used
 * while measuring -- a car drawn from the stored angles, not a stored
 * screenshot, so it costs (almost) no extra storage and stays crisp at any
 * size. Camber is a head-on view (front axle on top); Toe is top-down
 * (front pointing up), matching the measuring screens.
 */
@Composable
private fun SessionDetailDialog(session: SavedSession, onDismiss: () -> Unit) {
    val values = session.measurements.associate { it.wheel to it.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(session.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${session.mode}", color = Color.Gray, fontSize = 13.sp)

                Text("\n", fontSize = 4.sp)
                if (session.mode == "Camber") {
                    AxleVisual(
                        label = "FRONT",
                        leftLabel = "Front-left",
                        rightLabel = "Front-right",
                        values = values,
                        isCamber = true
                    )
                    AxleVisual(
                        label = "REAR",
                        leftLabel = "Rear-left",
                        rightLabel = "Rear-right",
                        values = values,
                        isCamber = true
                    )
                } else {
                    AxleVisual(
                        label = "FRONT",
                        leftLabel = "Front-left",
                        rightLabel = "Front-right",
                        values = values,
                        isCamber = false
                    )
                    AxleVisual(
                        label = "REAR",
                        leftLabel = "Rear-left",
                        rightLabel = "Rear-right",
                        values = values,
                        isCamber = false
                    )
                }

                Text("\nValues", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                session.measurements.forEach { m ->
                    Text(
                        "  \u2022  ${m.wheel}:  %.2f\u00b0".format(m.value),
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}

/**
 * One axle row of the recreation: two tires leaning/rotated by that
 * wheel's stored angle, with the left-minus-right split (camber) or the
 * total-toe measurement (toe) in the middle. Mirrors the measuring screens'
 * exaggeration so small real angles stay visible.
 */
@Composable
private fun AxleVisual(
    label: String,
    leftLabel: String,
    rightLabel: String,
    values: Map<String, Double>,
    isCamber: Boolean
) {
    val left = values[leftLabel]?.toFloat() ?: 0f
    val right = values[rightLabel]?.toFloat() ?: 0f
    val exaggeration = if (isCamber) CamberVisualExaggeration else ToeVisualExaggeration

    // Same sign convention as the live screens: camber left negates, right
    // stays; toe left stays, right negates.
    val leftRotation = if (isCamber) -(left) * exaggeration else (left) * exaggeration
    val rightRotation = if (isCamber) (right) * exaggeration else -(right) * exaggeration

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TireVisual(label = leftLabel, value = left, rotationDeg = clampSessionRotation(leftRotation))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                text = if (isCamber) {
                    "%.2f\u00b0".format(left - right) + "\nSplit"
                } else {
                    val totalKey = if (label == "FRONT") "Front total toe" else "Rear total toe"
                    (values[totalKey]?.let { "%.2f\u00b0".format(it) } ?: "---") + "\nTotal"
                },
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }
        TireVisual(label = rightLabel, value = right, rotationDeg = clampSessionRotation(rightRotation))
    }
}

/** A single non-interactive tire in the session recreation. */
@Composable
private fun TireVisual(label: String, value: Float, rotationDeg: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.tire),
            contentDescription = "$label tire",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 52.dp, height = 96.dp)
                .graphicsLayer { rotationZ = rotationDeg }
        )
        Text(label, fontSize = 11.sp)
        Text(
            "%.2f\u00b0".format(value),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun clampSessionRotation(degrees: Float): Float =
    degrees.coerceIn(-45f, 45f)

/** Match the measuring screens' exaggeration (camber 25x, toe 15x). */
private const val CamberVisualExaggeration = 25f
private const val ToeVisualExaggeration = 15f
