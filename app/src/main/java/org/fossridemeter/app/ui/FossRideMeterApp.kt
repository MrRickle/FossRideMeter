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

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import org.fossridemeter.app.service.AmountCalculator
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.Stop

@Composable
fun FossRideMeter(
    rideViewModel: RideViewModel,
    places: Map<String, Place>,
    stopsByRide: Map<String, List<Stop>>,
    onUpdatePlace: (Place) -> Unit,
) {

    val context = LocalContext.current

    val hasLocationPermission =
        remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasLocationPermission.value = granted
        }

    LaunchedEffect(hasLocationPermission.value) {
        if (hasLocationPermission.value) {
            rideViewModel.startServiceIfNeeded()
        }
    }

    if (!hasLocationPermission.value) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text("FossRideMeter needs location permission.")

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                ) {
                    Text("Grant Permission")
                }
            }
        }

        return
    }

    val gpsInfo by rideViewModel.gpsInfo.collectAsState()
    val autoState by rideViewModel.autoState.collectAsState()
    val watchFix by rideViewModel.watchFix.collectAsState()
    val settings by rideViewModel.settings.collectAsState()
    val ride by remember(rideViewModel.ride) { rideViewModel.ride }.collectAsState()

    val displayRide = remember(ride, settings) {
        AmountCalculator.calculate(ride, settings)
    }

    RideScreen(
        ride = displayRide,
        gpsInfo = gpsInfo,
        settings = settings,
        places = places,
        // The live ride's stops are already in the database - RideMeter
        // writes each one at detection time - so they come from the same
        // grouped map the Rides screen reads, keyed by this ride's id.
        stops = stopsByRide[displayRide.id].orEmpty(),
        autoState = autoState,
        watchFix = watchFix,
        onUpdatePlace = onUpdatePlace,
        onStart = { rideViewModel.start() },
        onPause = { rideViewModel.pause() },
        onResume = { rideViewModel.resume() },
        onAddStop = { rideViewModel.addStop() },
        onSave = { rideViewModel.save() },
        onCancel = {rideViewModel.cancel() },
        onNameChanged = { rideViewModel.updateName(it) },
        onManualAmountChanged = { rideViewModel.updateManualAmount(it) },
    )
}
