package com.example.gyrolinediy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A short checklist shown before every measurement session, mirroring the
 * "Safe Operating Conditions" section from commercial alignment tools.
 * These conditions matter because the sensor is only reporting the wheel's
 * angle relative to gravity/heading -- if the car itself isn't in a
 * consistent, neutral state, the reading will be accurate but meaningless
 * (e.g. camber measured with the car leaning on a slope, or toe measured
 * with the steering wheel turned, isn't the car's real alignment).
 *
 * Continue is disabled until every item is checked.
 */
@Composable
fun PreflightScreen(
    modeName: String,
    onContinue: () -> Unit
) {
    var levelGround by remember { mutableStateOf(false) }
    var steeringCentered by remember { mutableStateOf(false) }
    var parkingBrakeOn by remember { mutableStateOf(false) }
    var tiresInflated by remember { mutableStateOf(false) }
    var suspensionSettled by remember { mutableStateOf(false) }
    var topTowardFront by remember { mutableStateOf(false) }
    var bodyReferencePicked by remember { mutableStateOf(false) }

    val allChecked = levelGround && steeringCentered && parkingBrakeOn &&
        tiresInflated && suspensionSettled &&
        (modeName != "Toe" || (topTowardFront && bodyReferencePicked))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Before measuring $modeName", fontWeight = FontWeight.Bold)
        Text("\nCheck each item off before continuing:")

        ChecklistItem(
            checked = levelGround,
            onCheckedChange = { levelGround = it },
            label = "The car is parked on flat, level ground"
        )
        ChecklistItem(
            checked = steeringCentered,
            onCheckedChange = { steeringCentered = it },
            label = "The steering wheel is centered (straight-ahead position)"
        )
        ChecklistItem(
            checked = parkingBrakeOn,
            onCheckedChange = { parkingBrakeOn = it },
            label = "The parking brake is engaged"
        )
        ChecklistItem(
            checked = tiresInflated,
            onCheckedChange = { tiresInflated = it },
            label = "All tires are inflated to the correct pressure"
        )
        ChecklistItem(
            checked = suspensionSettled,
            onCheckedChange = { suspensionSettled = it },
            label = "The suspension has settled (drive/roll the car a few " +
                "feet forward and back, or bounce each corner, right before measuring)"
        )

        if (modeName == "Toe") {
            ChecklistItem(
                checked = topTowardFront,
                onCheckedChange = { topTowardFront = it },
                label = "For every wheel, I will place the phone flat " +
                    "against the outer rim edge with its screen facing up " +
                    "toward the sky and the TOP of the phone pointing " +
                    "toward the FRONT of the car"
            )
            ChecklistItem(
                checked = bodyReferencePicked,
                onCheckedChange = { bodyReferencePicked = it },
                label = "I know which car-body spot I'll use as the " +
                    "reference (e.g. the pinch-weld seam under the rocker " +
                    "panel) and will use the SAME spot on each side "
            )
        }

        Button(
            onClick = onContinue,
            enabled = allChecked,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Continue")
        }

        if (!allChecked) {
            Text(
                "\nAll boxes need to be checked -- each of these can throw " +
                    "off the reading if it's not true.",
                color = androidx.compose.ui.graphics.Color.Gray
            )
        }
    }
}

@Composable
private fun ChecklistItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
