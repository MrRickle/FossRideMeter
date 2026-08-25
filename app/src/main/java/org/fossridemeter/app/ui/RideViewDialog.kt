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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.fossridemeter.app.data.HAND_PLACED_RADIUS_METERS
import org.fossridemeter.app.data.placeAt
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Stop

/**
 * A ride: what it was, where it went, what it came to.
 *
 * There is no edit mode and no Edit button. A ride has exactly two things
 * that can be changed after the fact - its name and its amount - and
 * those two rows are edited by tapping them, the same gesture that opens
 * a place from its row. A form built around two editable fields and a
 * dozen uneditable ones only invited the question of why the rest weren't.
 *
 * This owns everything reachable from a ride's detail: the stops list,
 * the place editor, and the one-field editors for the two rows that take
 * one.
 */
@Composable
fun RideViewDialog(
    ride: RideRecord,
    settings: Settings,
    places: Map<String, Place>,
    stops: List<Stop>,
    onUpdateRide: (RideRecord) -> Unit,
    onUpdatePlace: (Place) -> Unit,
    onDelete: (RideRecord) -> Unit,
    onDismiss: () -> Unit,
) {

    var showStopsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Hosted once here so the start/end rows and the stops list share one
    // instance. Dialogs stack, so it appears above whichever opened it.
    var placeBeingEdited by remember { mutableStateOf<Place?>(null) }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(
                text = ride.name.ifBlank { "Ride" },
                style = MaterialTheme.typography.titleLarge
            )
        },

        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                RideInfoSection(
                    info = ride.toRideInfo(places = places, stops = stops),
                    settings = settings,
                    places = places,
                    onOpenStops = { showStopsDialog = true },
                    onOpenPlace = { placeBeingEdited = it },

                    // Written straight onto the row as it currently
                    // stands - this composable is handed a freshly looked
                    // up record each recomposition, so there is no
                    // captured copy to go stale between opening the
                    // dialog and confirming the field.
                    onNameChanged = { onUpdateRide(ride.copy(name = it)) },
                    onAmountChanged = { onUpdateRide(ride.copy(manualAmount = it)) },
                )
            }
        },

        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },

        dismissButton = {
            TextButton(onClick = { showDeleteDialog = true }) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )

    if (showDeleteDialog) {
        RideDeleteDialog(
            ride = ride,
            onDelete = { onDelete(ride) },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showStopsDialog) {
        StopsDialog(
            stops = stops,
            places = places,
            onDismiss = { showStopsDialog = false },
            onOpenPlace = { placeBeingEdited = it },

            // The place doesn't exist yet - it's handed to the same
            // editor unsaved, and the same save path writes it. A new
            // row and an edited one behave identically from there.
            onNameSpot = { location ->
                placeBeingEdited = placeAt(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = HAND_PLACED_RADIUS_METERS,
                )
            },
        )
    }

    placeBeingEdited?.let { place ->

        // Saves through the same callback the Places screen uses, so
        // PlaceBoundaryEnforcer runs and every ride affected by a changed
        // radius is re-resolved - not just the one being viewed.
        PlaceEditDialog(
            place = place,
            settings = settings,
            // Nothing to remember: a place built from a stop has an id no
            // row carries yet, which is exactly what "new" means here.
            isNew = place.id !in places,
            onSave = { updated ->
                onUpdatePlace(updated)
                placeBeingEdited = null
            },
            onDismiss = { placeBeingEdited = null }
        )
    }
}
