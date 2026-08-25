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

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Stop
import org.fossridemeter.app.util.displayAmount
import org.fossridemeter.app.util.displayDistance
import org.fossridemeter.app.util.displayDistanceRate
import org.fossridemeter.app.util.distanceUnit
import java.text.SimpleDateFormat
import java.util.Date

private val NameWidth = 220.dp
private val AmountWidth = 110.dp
private val DistanceWidth = 110.dp
private val StartWidth = 160.dp
private val StopsWidth = 200.dp
private val EndWidth = 160.dp
private val DurationWidth = 90.dp
private val TimeWidth = 220.dp
private val RateWidth = 110.dp
private val BaseWidth = 90.dp
private val MinWidth = 90.dp
private val LocationWidth = 240.dp
private val ProviderWidth = 100.dp

@Composable
fun RidesScreen(
    rides: List<RideRecord>,
    settings: Settings,
    places: Map<String, Place>,
    stopsByRide: Map<String, List<Stop>>,
    onUpdateRide: (RideRecord) -> Unit,
    onDeleteRide: (String) -> Unit,
    onUpdatePlace: (Place) -> Unit,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    // See PlacesScreen: an empty selection is "not selecting", and while
    // selecting a tap toggles rather than opening the editor.
    val selecting = selectedIds.isNotEmpty()

    // Ids, not records. Holding the row itself would freeze it as it was
    // when the dialog opened, and it can legitimately change while open -
    // editing a place re-resolves the rides linked to it. Looking it up
    // again each recomposition means the detail always shows the row as
    // it currently stands, and a ride deleted from anywhere else simply
    // closes its dialog rather than lingering as a stale copy.
    var viewingRideId by remember { mutableStateOf<String?>(null) }

    val viewingRide = viewingRideId?.let { id -> rides.firstOrNull { it.id == id } }

    val horizontalScroll = rememberScrollState()

    // Same field list/order as RideInfoSection - one shared formatter
    // instance reused across every row rather than one per row.
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) {
        SimpleDateFormat(
            "MMM d, yyyy  h:mm a",
            locale
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // No inline title here - the screen chrome (top bar) already
        // shows "Rides", so a second one right below it was redundant.
        // Import/Export live in the top bar's overflow menu (see
        // AppNavigation) rather than as buttons here - they're actions on
        // the screen, and the table below needs the vertical space.

        Row(
            modifier = Modifier
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 4.dp)
        ) {

            HeaderCell("Name", NameWidth)
            HeaderCell("Amount", AmountWidth)
            HeaderCell("Distance", DistanceWidth)
            HeaderCell("Start Place", StartWidth)
            HeaderCell("Stops", StopsWidth)
            HeaderCell("End Place", EndWidth)
            HeaderCell("Duration", DurationWidth)
            HeaderCell("Stopped", DurationWidth)
            HeaderCell("Start Time", TimeWidth)
            HeaderCell("End Time", TimeWidth)
            HeaderCell("Rate", RateWidth)
            HeaderCell("Per Hour", RateWidth)
            HeaderCell("Per Hour Stopped", RateWidth)
            HeaderCell("Base", BaseWidth)
            HeaderCell("Minimum", MinWidth)
            HeaderCell("Original Amount", AmountWidth)
            HeaderCell("Start Location", LocationWidth)
            HeaderCell("End Location", LocationWidth)
            HeaderCell("GPS Provider", ProviderWidth)

        }

        LazyColumn {

            items(rides) { ride ->

                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontalScroll)
                ) {

                    RidesRow(
                        ride = ride,
                        settings = settings,
                        places = places,
                        stops = stopsByRide[ride.id].orEmpty(),
                        formatter = formatter,
                        selected = ride.id in selectedIds,
                        onClick = {
                            if (selecting) {
                                onToggleSelection(ride.id)
                            } else {
                                Log.d("Rides", "Clicked ${ride.id}")
                                viewingRideId = ride.id
                            }
                        },
                        onLongClick = { onToggleSelection(ride.id) }
                    )
                }
            }
        }

        // Tapping a row opens the record. There is no edit mode: the
        // two rows that can be changed are edited by tapping them, and
        // what gets written is applied to the row as looked up here, so
        // nothing is captured long enough to go stale.
        viewingRide?.let { ride ->

            RideViewDialog(
                ride = ride,
                settings = settings,
                places = places,
                stops = stopsByRide[ride.id].orEmpty(),
                onUpdateRide = onUpdateRide,
                onUpdatePlace = onUpdatePlace,
                onDelete = {
                    onDeleteRide(it.id)
                    viewingRideId = null
                },
                onDismiss = { viewingRideId = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RidesRow(
    ride: RideRecord,
    settings: Settings,
    places: Map<String, Place>,
    stops: List<Stop>,
    formatter: SimpleDateFormat,
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

            DataCell(ride.name, NameWidth)

            DataCell(
                displayAmount(ride.manualAmount ?: ride.calculatedAmount),
                AmountWidth
            )

            DataCell(
                displayDistance(ride.meters, settings.measurementSystem.distanceUnit),
                DistanceWidth
            )

            DataCell(
                ride.startPlaceId?.let(places::get)?.name ?: "-",
                StartWidth
            )

            DataCell(
                // Just the legs in between - Start/End Place already have
                // their own columns, so repeating them here would just be
                // noise (unlike the full Start -> ... -> End chain shown
                // in RideInfoSection for a single saved ride).
                stops.groupConsecutiveByPlace()
                    .map { it.placeId?.let(places::get)?.name ?: "?" }
                    .let { names -> if (names.isEmpty()) "-" else names.joinToString(" \u2192 ") },
                StopsWidth
            )

            DataCell(
                ride.endPlaceId?.let(places::get)?.name ?: "-",
                EndWidth
            )

            DataCell(
                formatElapsedTime(ride.elapsedSeconds),
                DurationWidth
            )

            DataCell(
                // A dash rather than 0:00 for a ride that never stopped:
                // the column is about whether there was stopped time at
                // all, and a zero reads like a measurement.
                if (ride.stoppedSeconds > 0) {
                    formatElapsedTime(ride.stoppedSeconds)
                } else {
                    "-"
                },
                DurationWidth
            )

            DataCell(
                formatter.format(Date(ride.startTime)),
                TimeWidth
            )

            DataCell(
                formatter.format(Date(ride.endTime)),
                TimeWidth
            )

            DataCell(
                displayDistanceRate(ride.perMeterRate, settings.measurementSystem.distanceUnit),
                RateWidth
            )

            DataCell(
                displayAmount(ride.hourlyRate),
                RateWidth
            )

            DataCell(
                ride.stoppedHourlyRate?.let(::displayAmount) ?: "-",
                RateWidth
            )

            DataCell(
                displayAmount(ride.baseAmount),
                BaseWidth
            )

            DataCell(
                displayAmount(ride.minimumAmount),
                MinWidth
            )

            DataCell(
                displayAmount(ride.calculatedAmount),
                AmountWidth
            )

            DataCell(
                ride.startLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "Unknown",
                LocationWidth
            )

            DataCell(
                ride.endLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "Unknown",
                LocationWidth
            )

            DataCell(
                ride.distanceProvider.displayName,
                ProviderWidth
            )
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp
) {

    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(4.dp),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
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
            .padding(4.dp),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
