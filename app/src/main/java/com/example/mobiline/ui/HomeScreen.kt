package com.example.mobiline.ui

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

@Composable
fun HomeScreen(
    onSelectCamber: () -> Unit,
    onSelectToe: () -> Unit,
    onRecalibrate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MobiLine", fontWeight = FontWeight.Bold)
        Text("What do you want to measure?")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onSelectCamber, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Camber")
            }
            Text(
                "How far a wheel leans in or out when you look at the car " +
                    "head-on. One wheel, one reading.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Button(onClick = onSelectToe, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Toe")
            }
            Text(
                "Whether the wheels point slightly toward or away from " +
                    "each other when you look down from above. Needs all " +
                    "four wheels measured, one side of the car at a time.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Button(onClick = onRecalibrate, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Recalibrate")
            }
            Text(
                "Start over from Step 1 if your readings seem off, or if " +
                    "you moved the phone in its holder.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}
