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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.fossridemeter.app.model.RideRecord

@Composable
fun RideDeleteDialog(
    ride: RideRecord,
    onDelete: (RideRecord) -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Delete Ride?")
        },

        text = {

            Text(
                "Delete \"${ride.name}\"?\n\nThis cannot be undone."
            )
        },

        confirmButton = {

            Button(

                onClick = {

                    onDelete(
                        ride
                    )

                    onDismiss()
                }

            ) {

                Text("Delete")
            }
        },

        dismissButton = {

            OutlinedButton(

                onClick = onDismiss

            ) {

                Text("Cancel")
            }
        }
    )
}