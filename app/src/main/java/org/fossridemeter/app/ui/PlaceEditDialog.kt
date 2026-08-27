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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.ClipData
import kotlinx.coroutines.launch
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.util.abbreviation
import org.fossridemeter.app.util.distanceToUnit
import org.fossridemeter.app.util.gpsAccuracyUnit
import org.fossridemeter.app.util.unitToDistance
import org.fossridemeter.app.util.openInMaps

/**
 * Location and radius are free-text so coordinates can be pasted straight
 * from whatever map app the user has open (Google Maps' "copy
 * coordinates" gives exactly "lat, lng"), and copied back out the same
 * way to paste into that app for navigation. Saving here always marks the
 * place as named - see PlacesViewModel.updatePlace, which also writes it
 * for the first time when the place is one the stops list has just
 * proposed.
 *
 * Radius displays/edits in gpsAccuracyUnit (feet for US, meters for SI) -
 * the right scale for something tens of meters across, unlike
 * distanceUnit (miles/kilometers), which is sized for ride distances.
 */
@Composable
fun PlaceEditDialog(
    place: Place,
    settings: Settings,
    onSave: (Place) -> Unit,
    onDismiss: () -> Unit,
    // Set when [place] hasn't been saved yet - a spot named from the
    // stops list. Only the title differs; a new place is edited, saved
    // and re-resolved exactly like an existing one.
    isNew: Boolean = false,
    // Supplied only where deleting a place is what the screen is for,
    // which is the Places list. The same dialog opens from a stop and
    // from the live ride, and offering to delete a place from inside a
    // ride would be an outsized action to find there - so those pass
    // nothing and the button doesn't render. A place that isn't saved
    // yet has nothing to delete either.
    onDelete: ((Place) -> Unit)? = null,
) {

    val radiusUnit = settings.measurementSystem.gpsAccuracyUnit

    var name by remember(place.id) { mutableStateOf(place.name) }

    var locationText by remember(place.id) {
        mutableStateOf("%.6f, %.6f".format(place.latitude, place.longitude))
    }
    var locationError by remember(place.id) { mutableStateOf(false) }

    var radiusText by remember(place.id) {
        mutableStateOf("%.0f".format(distanceToUnit(place.radiusMeters, radiusUnit)))
    }
    var radiusError by remember(place.id) { mutableStateOf(false) }

    var autoStart by remember(place.id) { mutableStateOf(place.autoStart) }
    var autoSave by remember(place.id) { mutableStateOf(place.autoSave) }

    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDeleteDialog by remember(place.id) { mutableStateOf(false) }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(
                text = if (isNew) "New Place" else "Edit Place",
                style = MaterialTheme.typography.titleLarge
            )
        },

        text = {

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place Name") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = locationText,
                    onValueChange = {
                        locationText = it
                        locationError = false
                    },
                    label = { Text("Location (lat, lng)") },
                    singleLine = true,
                    isError = locationError,
                    supportingText = {
                        if (locationError) {
                            Text("Couldn't read that as \"lat, lng\"")
                        }
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    val pasted = clipboard.getClipEntry()
                                        ?.clipData
                                        ?.getItemAt(0)
                                        ?.text
                                        ?.toString()
                                    pasted?.let {
                                        locationText = it
                                        locationError = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste location")
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Place location", locationText))
                                    )
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy location")
                            }
                        }
                    }
                )

                // Hands the point to a maps app so it can be checked on
                // the ground; the corrected coordinates come back
                // through Paste. It reads the field rather than the
                // saved place, because what is being checked is what is
                // typed.
                //
                // A labelled button rather than a third trailing icon:
                // three of them squeezed the coordinates down to
                // "37.776982, -122." and being able to read them is half
                // of what that field is for.
                TextButton(
                    onClick = {
                        val point = parseLatLng(locationText)
                        if (point == null) {
                            locationError = true
                        } else {
                            openInMaps(
                                context = context,
                                latitude = point.first,
                                longitude = point.second,
                                label = name,
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Show on a map")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = radiusText,
                    onValueChange = {
                        radiusText = it
                        radiusError = false
                    },
                    label = { Text("Radius (${radiusUnit.abbreviation()})") },
                    singleLine = true,
                    isError = radiusError,
                    supportingText = {
                        if (radiusError) {
                            Text("Enter a number greater than 0")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Auto-start when leaving", modifier = Modifier.weight(1f))
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Auto-save when arriving", modifier = Modifier.weight(1f))
                    Switch(checked = autoSave, onCheckedChange = { autoSave = it })
                }
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    val point = parseLatLng(locationText)
                    val enteredRadius = radiusText.toDoubleOrNull()

                    if (point == null) {
                        locationError = true
                        return@Button
                    }

                    val (lat, lng) = point
                    if (enteredRadius == null || enteredRadius <= 0.0) {
                        radiusError = true
                        return@Button
                    }

                    val radiusMeters = unitToDistance(enteredRadius, radiusUnit)

                    onSave(
                        place.copy(
                            name = name,
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = radiusMeters,
                            autoStart = autoStart,
                            autoSave = autoSave,
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },

        dismissButton = {

            // Delete to the left of Cancel, in the slot AlertDialog puts
            // furthest from Save - the same arrangement a ride's detail
            // uses, so the destructive button is never where the finger
            // is already going. M3 wraps these onto a second line if the
            // three don't fit.
            Row(verticalAlignment = Alignment.CenterVertically) {

                if (onDelete != null && !isNew) {

                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                }

                OutlinedButton(
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showDeleteDialog && onDelete != null) {

        // Deleting closes the editor too: what it was editing is gone,
        // and leaving it open over a deleted row is how a stale save
        // gets written back.
        PlaceDeleteDialog(
            place = place,
            onDelete = {
                onDelete(it)
                onDismiss()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * "lat, lng" as the location field accepts it, or null when it isn't
 * that. Shared by Save and by the map button so the two can never
 * disagree about what counts as a location.
 */
private fun parseLatLng(text: String): Pair<Double, Double>? {

    val parts = text.split(",").map { it.trim() }
    if (parts.size != 2) return null

    val latitude = parts[0].toDoubleOrNull() ?: return null
    val longitude = parts[1].toDoubleOrNull() ?: return null

    return latitude to longitude
}
