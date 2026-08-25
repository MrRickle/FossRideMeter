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
import org.fossridemeter.app.model.Place

/**
 * Confirms deleting one place, from its editor.
 *
 * The wording says what happens to the history rather than "this cannot
 * be undone", because for a place that isn't quite true and the
 * difference matters: the rides and stops recorded here keep their
 * points and come back under a geohash placeholder. What is lost is the
 * name, the radius, and any auto-start or auto-save flag - so the thing
 * worth warning about is the watching that stops, not the data.
 */
@Composable
fun PlaceDeleteDialog(
    place: Place,
    onDelete: (Place) -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Delete Place?")
        },

        text = {

            Text(
                "Delete \"${place.name}\"?\n\n" +
                        "Rides and stops here keep their locations. They go " +
                        "back to an unnamed place.\n\n" +
                        "The name and radius cannot be recovered." +
                        if (place.autoStart || place.autoSave) {
                            "\n\nThis place will stop starting and saving " +
                                    "rides automatically."
                        } else {
                            ""
                        }
            )
        },

        confirmButton = {

            Button(

                onClick = {
                    onDelete(place)
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
