package com.justan.mobiline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justan.mobiline.data.SavedMeasurement
import com.justan.mobiline.data.SavedSession
import com.justan.mobiline.data.SessionStore
import com.justan.mobiline.data.defaultSessionName
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The "save this session" block. Only renders once [enabled] (all wheels
 * measured). The name field is prefilled with the current date + time and can
 * be edited before saving; saving to [store] is async and shows a small
 * confirmation.
 */
@Composable
fun SessionSaveSection(
    store: SessionStore,
    mode: String,
    enabled: Boolean,
    buildMeasurements: () -> List<SavedMeasurement>,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var name by remember { mutableStateOf(defaultSessionName()) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Re-prefill the name the moment the save option appears.
    LaunchedEffect(Unit) {
        name = defaultSessionName()
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (saved)
                "\nSession saved \u2713"
            else
                "\nAll wheels measured -- save this session?"
        )

        if (!saved) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Button(
                onClick = {
                    scope.launch {
                        store.save(
                            SavedSession(
                                id = UUID.randomUUID().toString(),
                                name = name.ifBlank { defaultSessionName() },
                                createdAt = System.currentTimeMillis(),
                                mode = mode,
                                measurements = buildMeasurements()
                            )
                        )
                        saved = true
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Save session")
            }
        }
    }
}