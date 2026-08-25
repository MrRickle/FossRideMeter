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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Edit one field, in place, from the row that shows it.
 *
 * The Ride Information rows are the ride, so the two of them that can be
 * changed are changed by tapping them - the same gesture that opens a
 * place from its row. That leaves no separate "edit mode" for a ride at
 * all: what you see is what you edit, one field at a time.
 */
@Composable
fun FieldEditDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {

    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = { Text(title) },

        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                supportingText = supportingText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        },

        confirmButton = {
            Button(
                onClick = {
                    onConfirm(value)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },

        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
