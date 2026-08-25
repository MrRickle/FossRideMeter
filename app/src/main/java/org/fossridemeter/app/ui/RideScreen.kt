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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.service.AutoState
import org.fossridemeter.app.service.PlaceWatcher
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.Stop
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.ui.theme.FossRideMeterTheme
import org.fossridemeter.app.service.DistanceStatus
import org.fossridemeter.app.service.GpsInfo
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.ui.theme.StatusColors
import org.fossridemeter.app.util.displayAmount
import org.fossridemeter.app.util.displayDistance
import org.fossridemeter.app.util.displayElapsedTime
import org.fossridemeter.app.util.distanceUnit
import org.fossridemeter.app.util.gpsAccuracyUnit
import androidx.compose.ui.platform.LocalWindowInfo


@Composable
fun RideScreen(
    ride: Ride,
    gpsInfo: GpsInfo,
    settings: Settings,
    places: Map<String, Place> = emptyMap(),
    stops: List<Stop> = emptyList(),
    autoState: AutoState = AutoState.Off,
    watchFix: PlaceWatcher.WatchFix? = null,
    onUpdatePlace: (Place) -> Unit = {},
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNameChanged: (String) -> Unit,
    onManualAmountChanged: (Double?) -> Unit,
    //modifier: Modifier = Modifier
) {
    KeepScreenOn(keepScreenOn = ride.status == RideStatus.RUNNING)

    val screenWidthDp = LocalWindowInfo.current.containerSize.width.dp
    val screenHeightDp = LocalWindowInfo.current.containerSize.height.dp
    val isLandscape = screenWidthDp > screenHeightDp
    // Purely "is there room". *What* goes in the space depends on the
    // ride's status and is RideLiveInfoSection's decision: the two
    // fill-in-ahead fields while READY, the ride's detail once there is a
    // ride to detail.
    val showInfoSection = if (isLandscape) {
        screenWidthDp >= 500.dp
    } else {
        screenHeightDp >= 500.dp
    }

    if (isLandscape) {
        RideLandscape(
            ride = ride,
            gpsInfo = gpsInfo,
            settings = settings,
            places = places,
            stops = stops,
            autoState = autoState,
            watchFix = watchFix,
            onUpdatePlace = onUpdatePlace,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onAddStop = onAddStop,
            onSave = onSave,
            onCancel = onCancel,
            onNameChanged = onNameChanged,
            onManualAmountChanged = onManualAmountChanged,
            showInfoSection = showInfoSection,
        )
    } else {
        RidePortrait(
            ride = ride,
            gpsInfo = gpsInfo,
            settings = settings,
            places = places,
            stops = stops,
            autoState = autoState,
            watchFix = watchFix,
            onUpdatePlace = onUpdatePlace,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onAddStop = onAddStop,
            onSave = onSave,
            onCancel = onCancel,
            onNameChanged = onNameChanged,
            onManualAmountChanged = onManualAmountChanged,
            showInfoSection = showInfoSection,
        )
    }
}


@Composable
private fun RidePortrait(
    ride: Ride,
    gpsInfo: GpsInfo,
    settings: Settings,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNameChanged: (String) -> Unit,
    onManualAmountChanged: (Double?) -> Unit,
    showInfoSection: Boolean,
    places: Map<String, Place>,
    stops: List<Stop>,
    autoState: AutoState,
    watchFix: PlaceWatcher.WatchFix?,
    onUpdatePlace: (Place) -> Unit,
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        RideLive(
            ride = ride,
            gpsInfo = gpsInfo,
            settings = settings,
            autoState = autoState,
            watchFix = watchFix,
            showWatchStatus = !showInfoSection,
            showDetails = true,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onAddStop = onAddStop,
            onSave = onSave,
            onCancel = onCancel,
        )

        if (showInfoSection) {
            RideLiveInfoSection(
                ride = ride,
                settings = settings,
                places = places,
                stops = stops,
                autoState = autoState,
                watchFix = watchFix,
                onNameChanged = onNameChanged,
                onManualAmountChanged = onManualAmountChanged,
                onUpdatePlace = onUpdatePlace,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RideLandscape(
    ride: Ride,
    gpsInfo: GpsInfo,
    settings: Settings,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNameChanged: (String) -> Unit,
    onManualAmountChanged: (Double?) -> Unit,
    showInfoSection: Boolean,
    places: Map<String, Place>,
    stops: List<Stop>,
    autoState: AutoState,
    watchFix: PlaceWatcher.WatchFix?,
    onUpdatePlace: (Place) -> Unit,
) {

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {

        RideLive(
            ride = ride,
            gpsInfo = gpsInfo,
            settings = settings,
            autoState = autoState,
            watchFix = watchFix,
            showWatchStatus = !showInfoSection,
            showDetails = !showInfoSection,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onAddStop = onAddStop,
            onSave = onSave,
            onCancel = onCancel,
        )

        if (showInfoSection) {
            RideLiveInfoSection(
                ride = ride,
                settings = settings,
                places = places,
                stops = stops,
                autoState = autoState,
                watchFix = watchFix,
                leading = {
                    RideMeterDetails(
                        ride = ride,
                        gpsInfo = gpsInfo,
                        settings = settings,
                    )
                },
                onNameChanged = onNameChanged,
                onManualAmountChanged = onManualAmountChanged,
                onUpdatePlace = onUpdatePlace,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun RideLive(
    ride: Ride,
    gpsInfo: GpsInfo,
    settings: Settings,
    autoState: AutoState,
    watchFix: PlaceWatcher.WatchFix?,
    // The screen reads in one order whichever way it is held - total,
    // buttons, meter details, watch status, ride information - and
    // landscape simply wraps that order into a second column after the
    // buttons. These two say which side of the wrap this column is
    // drawing; the info section draws the rest. Both are true only when
    // there is no room for an info section at all.
    showWatchStatus: Boolean,
    showDetails: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.width(330.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        RideTotal(ride)

        // The controls sit directly under the total, and nothing is
        // allowed between them: that gap is what used to grow - three
        // lines of watch status, two more data rows when a base or
        // minimum amount is set - until the buttons went off the bottom
        // of a landscape screen. Everything else follows them.
        RideButtons(
            ride = ride,
            // The stop is recorded where the vehicle is, so it needs a
            // fix to record. gpsInfo keeps the last one through a pause,
            // which is why this stays true while PAUSED.
            canAddStop = gpsInfo.rideLocation != null,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onAddStop = onAddStop,
            onSave = onSave,
            onCancel = onCancel,
        )

        if (showDetails) {
            RideMeterDetails(
                ride = ride,
                gpsInfo = gpsInfo,
                settings = settings,
            )
        }

        if (showWatchStatus) {
            RideWatchStatus(
                autoState = autoState,
                watchFix = watchFix,
            )
        }
    }
}

/**
 * The GPS line and the data table. One composable because the two of them
 * move together across the landscape wrap - see showDetails in RideLive.
 */
@Composable
private fun RideMeterDetails(
    ride: Ride,
    gpsInfo: GpsInfo,
    settings: Settings,
) {

    RideGpsStatus(
        gpsInfo = gpsInfo,
        settings = settings,
    )

    RideDataTable(ride, settings)
}

@Composable
private fun RideTotal(
    ride: Ride
) {

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            "Total",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * 2
            )
        )

        Text(
            "$%.2f".format(ride.totalAmount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * 2
            )
        )
    }
}

@Composable
private fun RideGpsStatus(
    gpsInfo: GpsInfo,
    settings: Settings,
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val movingText =
            if (gpsInfo.moving) "Moving"
            else "Not Moving"

        val unit = settings.measurementSystem.gpsAccuracyUnit

        // Same semantics as the control button, so a color reads the same way
        // everywhere in the app — see theme/StatusColors.kt.
        val statusColor =
            when (gpsInfo.status) {
                DistanceStatus.OFF -> StatusColors.stop
                DistanceStatus.WAITING -> StatusColors.idle
                DistanceStatus.POOR -> StatusColors.caution
                DistanceStatus.GOOD -> StatusColors.go
            }

        Text(
            text = when (gpsInfo.status) {
                DistanceStatus.OFF -> "GPS Off"
                DistanceStatus.WAITING -> "GPS Waiting"
                DistanceStatus.POOR -> "GPS Poor, ${
                    displayDistance(gpsInfo.accuracy.toDouble(), unit)
                }, $movingText"

                DistanceStatus.GOOD -> "GPS Good, ${
                    displayDistance(gpsInfo.accuracy.toDouble(), unit)
                }, $movingText"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor.content,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}


@Composable
fun RideDataTable(
    ride: Ride,
    settings: Settings
) {
    Column(
        modifier = Modifier
            .padding(
                horizontal = 20.dp
            )

    ) {

        HorizontalDivider()

        // Data Rows
        val unit = settings.measurementSystem.distanceUnit

        DataRow(
            value = displayDistance(ride.meters, unit),
            amount = displayAmount(ride.distanceAmount),
        )

        DataRow(
            value = displayElapsedTime(ride.elapsedSeconds),
            amount = displayAmount(ride.timeAmount),
        )

        if (settings.baseAmount != 0.0) {
            DataRow(
                value = "Base Amount",
                amount = displayAmount(settings.baseAmount),
            )
        }

        if (settings.minimumAmount != 0.0) {
            DataRow(
                value = "Minimum Amount",
                amount = displayAmount(settings.minimumAmount),
            )
        }
    }
}


@Composable
private fun RideButtons(
    ride: Ride,
    canAddStop: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // START / PAUSE / RESUME, whichever this status calls for, and
        // once there is a ride to add one to, ADD STOP in the space left
        // over beside it. Sharing the row costs no vertical space at all,
        // which is the whole point - see the comment above RideButtons's
        // caller about what the gap under the total is allowed to grow
        // into.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            RideControlButton(
                status = ride.status,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume
            )

            if (ride.status == RideStatus.RUNNING || ride.status == RideStatus.PAUSED) {

                Spacer(Modifier.width(8.dp))

                RideAddStopButton(
                    onAddStop = onAddStop,
                    enabled = canAddStop,
                )
            }
        }

        when (ride.status) {

            // A ride in either live state is complete enough to save at
            // any moment, so both offer the same pair. Save asks for
            // confirmation before it ends the ride.
            RideStatus.RUNNING, RideStatus.PAUSED -> {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    RideSaveButton(
                        onSave = onSave,
                    )

                    RideCancelButton(
                        onCancel = onCancel
                    )
                }
            }

            RideStatus.READY -> {
                // Nothing to save or cancel yet.
            }
        }
    }
}


@Composable
private fun DataRow(
    value: String,
    amount: String,
) {

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {


        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                value,
                modifier = Modifier.weight(2f)
            )

            Text(
                amount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RideScreenPreview() {

    FossRideMeterTheme {

        RideScreen(
            ride = Ride(),
            gpsInfo = GpsInfo(),
            settings = Settings(),
            onStart = {},
            onPause = {},
            onResume = {},
            onAddStop = {},
            onSave = {},
            onCancel = {},
            onNameChanged = {},
            onManualAmountChanged = {},
        )
    }
}
