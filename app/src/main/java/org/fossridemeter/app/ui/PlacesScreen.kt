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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.util.PlaceNames
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.data.placeAt
import org.fossridemeter.app.data.HAND_PLACED_RADIUS_METERS
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.util.displayDistance
import org.fossridemeter.app.util.gpsAccuracyUnit

private val NameWidth = 200.dp
private val LocationWidth = 180.dp
private val RadiusWidth = 90.dp
private val NamedWidth = 80.dp
private val LockedWidth = 80.dp
private val AutoWidth = 80.dp

@Composable
fun PlacesScreen(
    places: List<Place>,
    settings: Settings,
    currentLocation: RideLocation? = null,
    onUpdatePlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    // An empty selection is what "not selecting" means - see
    // SelectionState. While selecting, a plain tap toggles rows instead
    // of opening the editor, so the mode can be worked through without
    // dialogs appearing on every touch.
    val selecting = selectedIds.isNotEmpty()

    var selectedPlace by remember {
        mutableStateOf<Place?>(null)
    }

    // A place being created rather than edited. Separate state because
    // the editor has to be told which it is: a place that does not exist
    // yet must not offer Delete.
    var newPlace by remember {
        mutableStateOf<Place?>(null)
    }

    val horizontalScroll = rememberScrollState()

    // Composed once rather than per row: the name shown here is the
    // same one the rides use, so five branches of one chain can be told
    // apart by the town containing them.
    val displayNames = remember(places, settings.placeNameDepth) {
        places.associate { place ->
            place.id to PlaceNames.composed(place, places, settings.placeNameDepth)
        }
    }

    // Enums survive a rotation on their own, which a data class holding
    // both halves would need a Saver for.
    var sortedBy by rememberSaveable { mutableStateOf<PlaceSort?>(null) }
    var ascending by rememberSaveable { mutableStateOf(true) }

    val sorted = places.sortedByColumn(sortedBy, ascending) { key ->
        when (key) {
            // By the name as shown, so sorting by name puts everything
            // in one town together rather than scattering five
            // identically named branches through the list.
            PlaceSort.Name ->
                compareBy(String.CASE_INSENSITIVE_ORDER) {
                    displayNames[it.id] ?: it.name
                }

            // By latitude then longitude: neither alone is an order
            // anyone means, but together they group places that are
            // near each other, which is what looking down this column
            // is for.
            PlaceSort.Location ->
                compareBy<Place> { it.latitude }.thenBy { it.longitude }

            PlaceSort.Radius -> compareBy { it.radiusMeters }
            PlaceSort.Named -> compareBy { it.isNamed }
            PlaceSort.Locked -> compareBy { it.locationLocked }

            // Places that do something automatic first when descending,
            // which is the question this column is usually asked.
            PlaceSort.Auto ->
                compareBy<Place> { it.autoStart || it.autoSave }
                    .thenBy { it.autoStart }
        }
    }

    val onSort: (PlaceSort) -> Unit = { key ->
        ascending = if (key == sortedBy) !ascending else true
        sortedBy = key
    }

    Box(modifier = modifier) {

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // No inline title here - the screen chrome (top bar) already
        // shows "Places", so a second one right below it was redundant.
        // Import/Export live in the top bar's overflow menu (see
        // AppNavigation) rather than as buttons here - they're actions on
        // the screen, and the table below needs the vertical space.

        Row(
            modifier = Modifier
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 4.dp)
        ) {

            SortableHeaderCell("Name", NameWidth, PlaceSort.Name, sortedBy, ascending, onSort)
            SortableHeaderCell("Location", LocationWidth, PlaceSort.Location, sortedBy, ascending, onSort)
            SortableHeaderCell("Radius", RadiusWidth, PlaceSort.Radius, sortedBy, ascending, onSort)
            SortableHeaderCell("Named", NamedWidth, PlaceSort.Named, sortedBy, ascending, onSort)
            SortableHeaderCell("Locked", LockedWidth, PlaceSort.Locked, sortedBy, ascending, onSort)
            SortableHeaderCell("Auto", AutoWidth, PlaceSort.Auto, sortedBy, ascending, onSort)
        }

        LazyColumn {

            items(sorted) { place ->

                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontalScroll)
                ) {

                    PlaceRow(
                        place = place,
                        displayName = displayNames[place.id] ?: place.name,
                        settings = settings,
                        selected = place.id in selectedIds,
                        onClick = {
                            if (selecting) {
                                onToggleSelection(place.id)
                            } else {
                                selectedPlace = place
                            }
                        },
                        onLongClick = { onToggleSelection(place.id) }
                    )
                }
            }
        }

        selectedPlace?.let { place ->

            PlaceEditDialog(
                place = place,
                settings = settings,
                onSave = { updated ->
                    onUpdatePlace(updated)
                    selectedPlace = null
                },
                // Only here. A place opened from a stop or from the live
                // ride is being named, not managed - see PlaceEditDialog.
                onDelete = { deleted ->
                    onDeletePlace(deleted.id)
                    selectedPlace = null
                },
                onDismiss = {
                    selectedPlace = null
                }
            )
        }

        newPlace?.let { place ->

            PlaceEditDialog(
                place = place,
                settings = settings,
                isNew = true,
                onSave = { created ->
                    onUpdatePlace(created)
                    newPlace = null
                },
                // No onDelete: there is nothing to delete yet, and the
                // editor hides it for a new place anyway.
                onDismiss = {
                    newPlace = null
                }
            )
        }
    }

        // The only way to make a place that isn't where a ride already
        // stopped. Everything else - renaming an auto-created
        // placeholder, or tapping a stop's coordinates - requires having
        // been there and stood still long enough. A town is not a stop,
        // so drawing "Sparta" round a town meant repurposing whatever
        // placeholder happened to be near its middle.
        //
        // It opens on the current fix when there is one, because "add a
        // place where I am" is the common case. With no fix it opens on
        // the most recently added place, which is somewhere real and
        // obviously wrong rather than the Atlantic - and the location
        // row has Paste on it, which is how a place copied out of a maps
        // app gets here.
        FloatingActionButton(
            onClick = {
                val anchor = currentLocation
                    ?: places.maxByOrNull { it.createdAt }
                        ?.let { RideLocation(it.latitude, it.longitude) }

                newPlace = placeAt(
                    latitude = anchor?.latitude ?: 0.0,
                    longitude = anchor?.longitude ?: 0.0,
                    radiusMeters = HAND_PLACED_RADIUS_METERS,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "New place")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(
    place: Place,
    displayName: String,
    settings: Settings,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {

        Row(
            modifier = Modifier.padding(vertical = 2.dp)
        ) {

            DataCell(displayName, NameWidth)

            DataCell(
                "%.5f, %.5f".format(place.latitude, place.longitude),
                LocationWidth
            )

            DataCell(
                displayDistance(place.radiusMeters, settings.measurementSystem.gpsAccuracyUnit),
                RadiusWidth
            )

            DataCell(
                if (place.isNamed) "Yes" else "No",
                NamedWidth
            )

            DataCell(
                if (place.locationLocked) "Yes" else "No",
                LockedWidth
            )

            DataCell(
                buildString {
                    if (place.autoStart) append("S")
                    if (place.autoSave) append("A")
                }.ifEmpty { "-" },
                AutoWidth
            )
        }
    }
}


@Composable
private fun DataCell(
    text: String,
    width: Dp
) {

    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(4.dp)
    )
}

/** The Places table's columns, in the order they are shown. */
private enum class PlaceSort {
    Name, Location, Radius, Named, Locked, Auto
}
