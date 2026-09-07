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

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import org.fossridemeter.app.data.HAND_PLACED_RADIUS_METERS
import org.fossridemeter.app.data.placeAt
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Stop
import org.fossridemeter.app.service.AutoState
import org.fossridemeter.app.service.PlaceWatcher

/**
 * What sits below the running meter, which is one of two quite different
 * things depending on whether a ride exists yet.
 *
 * **READY** - no ride, so nothing to detail: just the two fields that can
 * be filled in ahead of one. A name for the run about to happen, or a
 * fare already agreed at the kerb. Neither is attached to anything yet;
 * RideMeter.start() carries whatever is in them onto the ride it creates.
 *
 * **RUNNING / PAUSED** - the ride's detail, the same rows a saved ride
 * shows, with the same place links and stops dialog. The name appears
 * there read-only; the fields themselves are gone, having done their job
 * before the ride began.
 */
@Composable
fun RideLiveInfoSection(
    ride: Ride,
    settings: Settings,
    places: Map<String, Place>,
    stops: List<Stop>,
    autoState: AutoState,
    watchFix: PlaceWatcher.WatchFix?,
    // Whatever fell on this side of the landscape wrap. The screen reads
    // in one order however it is held - total, buttons, meter details,
    // watch status, ride information - and in landscape the break comes
    // after the buttons, so the meter details arrive here and are drawn
    // first. Portrait keeps them in the column with the meter and passes
    // nothing.
    leading: @Composable () -> Unit = {},
    onNameChanged: (String) -> Unit,
    onManualAmountChanged: (Double?) -> Unit,
    onUpdatePlace: (Place) -> Unit,
    modifier: Modifier = Modifier
) {

    var showStopsDialog by remember { mutableStateOf(false) }
    var placeBeingEdited by remember { mutableStateOf<Place?>(null) }

    val view = LocalView.current
    val context = LocalContext.current

    // Re-sync the IME binding when returning from split-screen, so the
    // Ride Name / Override Amount fields don't lose their keyboard connection.
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val listener = Consumer<androidx.core.app.MultiWindowModeChangedInfo> { info ->
            if (!info.isInMultiWindowMode) {
                view.clearFocus()
                view.post {
                    view.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                    imm.restartInput(view)
                }
            }
        }
        activity?.addOnMultiWindowModeChangedListener(listener)
        onDispose {
            activity?.removeOnMultiWindowModeChangedListener(listener)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        leading()

        // Above the ride's own detail, and above the fill-in-ahead fields
        // while READY: this says whether the app is watching at all, which
        // outranks anything about a ride that may not exist yet. It draws
        // nothing when there is nothing to watch for.
        RideWatchStatus(
            autoState = autoState,
            watchFix = watchFix,
        )

        if (ride.status != RideStatus.READY) {

            Text(
                text = "Ride Details",
                style = MaterialTheme.typography.titleMedium
            )

            // Start/end places and the stops line are live links into the
            // place editor, exactly as on a saved ride.
            RideInfoSection(
                info = ride.toRideInfo(settings),
                settings = settings,
                places = places,
                onOpenStops = { showStopsDialog = true },
                onOpenPlace = { placeBeingEdited = it },
                // Same two editable rows as a saved ride, so a fare
                // agreed on arrival can still be entered without saving
                // first and going to the Rides screen for it.
                onNameChanged = onNameChanged,
                onAmountChanged = onManualAmountChanged,
            )
        }

        if (ride.status == RideStatus.READY) {

            var name by remember { mutableStateOf(ride.name) }

            LaunchedEffect(ride.name) {
                if (name != ride.name) {
                    name = ride.name
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onNameChanged(it)
                },
                label = { Text("Ride Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            var manualAmountText by remember {
                mutableStateOf(ride.manualAmount?.let { "%.2f".format(it) } ?: "")
            }

            LaunchedEffect(ride.manualAmount) {
                val current = manualAmountText.toDoubleOrNull()
                if (current != ride.manualAmount) {
                    manualAmountText = ride.manualAmount?.let { "%.2f".format(it) } ?: ""
                }
            }

            OutlinedTextField(
                value = manualAmountText,
                onValueChange = { input ->
                    manualAmountText = input
                    if (input.isBlank()) {
                        onManualAmountChanged(null)
                    } else {
                        input.toDoubleOrNull()?.let { onManualAmountChanged(it) }
                    }
                },
                label = { Text("Override Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

    }

    if (showStopsDialog) {
        StopsDialog(
            placeNameDepth = settings.placeNameDepth,
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

        // Same save path as everywhere else, so PlaceBoundaryEnforcer
        // runs - naming the place you're sitting in, mid-ride, is exactly
        // when this is most useful and it must behave identically.
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
