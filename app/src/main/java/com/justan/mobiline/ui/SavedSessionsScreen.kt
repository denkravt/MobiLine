package com.justan.mobiline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
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
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SavedSession) {
    val whenStr = Instant.ofEpochMilli(session.createdAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(session.name, fontWeight = FontWeight.Bold)
        Text("${session.mode}  \u00b7  $whenStr", fontSize = 12.sp, color = Color.Gray)
        session.measurements.forEach { m ->
            Text(
                "  \u2022  ${m.wheel}:  %.2f\u00b0".format(m.value),
                fontSize = 13.sp
            )
        }
    }
}