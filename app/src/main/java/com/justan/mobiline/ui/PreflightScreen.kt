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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A short informational screen shown before every measurement session.
 * Lists the conditions that matter for an accurate reading, then lets the
 * user continue -- no ticking required.
 */
@Composable
fun PreflightScreen(
    modeName: String,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSavedSessions: () -> Unit
) {
    val items = buildList {
        add("The car is parked on flat, level ground.")
        add("The steering wheel is centered (straight-ahead position).")
        add("The parking brake is engaged.")
        add("All tires are inflated to the correct pressure.")
        add(
            "The suspension has settled -- drive/roll the car a few feet " +
                "forward and back, or bounce each corner, right before measuring."
        )
        if (modeName == "Toe") {
            add(
                "For every wheel, place the phone flat against the outer rim " +
                    "edge with its screen facing UP toward the sky and the TOP " +
                    "of the phone pointing toward the FRONT of the car."
            )
            add(
                "Use the SAME car-body spot as a reference on each side " +
                    "(e.g. the pinch-weld seam under the rocker panel)."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(onBack = onBack, onSavedSessions = onSavedSessions)

        Text("Before measuring $modeName", fontWeight = FontWeight.Bold)

        Text(
            "\nRead each item below, then continue when you're ready:",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        items.forEach { item ->
            Text(
                "\n\u2022 $item",
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Continue")
        }

        Text(
            text = "\nEach of these conditions can throw off the reading if " +
                "it isn't true -- treat them as best practice, not red tape.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}