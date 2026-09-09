package com.justan.mobiline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Standard hamburger menu shown in a screen's header. Always offers
 * "Saved sessions"; when [onInfo] is provided it also offers "Info".
 */
@Composable
fun ScreenMenu(
    onSavedSessions: () -> Unit,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Saved sessions") },
                onClick = {
                    expanded = false
                    onSavedSessions()
                }
            )
            if (onInfo != null) {
                DropdownMenuItem(
                    text = { Text("Info") },
                    onClick = {
                        expanded = false
                        onInfo()
                    }
                )
            }
        }
    }
}

/**
 * Header bar for sub-screens: back arrow on the left, hamburger menu on the
 * right.
 */
@Composable
fun TopBar(
    onBack: () -> Unit,
    onSavedSessions: () -> Unit,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        ScreenMenu(onSavedSessions = onSavedSessions, onInfo = onInfo)
    }
}