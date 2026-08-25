// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * This file is part of FossRideMeter.
 * Copyright (C) 2026 Rick Hallock
 *
 * FossRideMeter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FossRideMeter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FossRideMeter. If not, see <https://www.gnu.org/licenses/>.
 *
 * As a special exception, this program may be combined and distributed
 * with the Google Play services client libraries - see
 * LICENSE-EXCEPTION.txt.
 */
package org.fossridemeter.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Saving ends the ride - there is no separate finished-but-not-saved
 * state to back out from, and the end place is resolved (creating a new
 * place if the ride ended somewhere unknown) on confirmation. So it
 * asks first, the same way cancelling does.
 */
@Composable
fun RideSaveButton(
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {

    var showConfirm by remember { mutableStateOf(false) }

    Button(
        onClick = { showConfirm = true },
        modifier = modifier
            .width(140.dp)
            .height(60.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "SAVE",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("End and save this ride?") },
            text = {
                Text(
                    "The ride ends where you are now. You can still edit " +
                            "it afterwards from the Rides screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onSave()
                }) { Text("Save ride") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Keep tracking") }
            }
        )
    }
}
