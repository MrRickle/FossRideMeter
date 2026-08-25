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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.model.Stop
import java.text.SimpleDateFormat
import java.util.Date

/**
 * View the sequence of places visited during a ride. The stops themselves
 * are read-only here - editing them, or entering a historical one with
 * date/time pickers, isn't built yet; adding one live during tracking is
 * the live screen's ADD STOP button. Each stop's place name opens that
 * place's editor, since a stop is very often where you first notice a
 * place needs naming or widening.
 *
 * Consecutive stops at one place are listed as one visit (see StopGroup),
 * with the dwells behind it shown underneath. Tapping a dwell's
 * coordinates makes a place there - the way to say that this particular
 * corner of the Home Depot is the lumber yard, from the one screen that
 * knows where it was.
 */
@Composable
fun StopsDialog(
    stops: List<Stop>,
    places: Map<String, Place>,
    onDismiss: () -> Unit,
    onOpenPlace: (Place) -> Unit = {},
    onNameSpot: (RideLocation) -> Unit = {},
) {

    val locale = LocalLocale.current.platformLocale

    val formatter = SimpleDateFormat("MMM d, h:mm a", locale)

    // The dwells inside a visit are minutes apart on the same afternoon -
    // repeating the date on each of them is noise.
    val timeFormatter = SimpleDateFormat("h:mm a", locale)

    val groups = stops.groupConsecutiveByPlace()

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Stops",
                style = MaterialTheme.typography.titleLarge
            )
        },

        text = {

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                if (groups.isEmpty()) {
                    Text("No stops recorded for this ride.")
                } else {
                    groups.forEachIndexed { index, group ->

                        StopGroupEntry(
                            position = index + 1,
                            group = group,
                            place = group.placeId?.let(places::get),
                            formatter = formatter,
                            timeFormatter = timeFormatter,
                            onOpenPlace = onOpenPlace,
                            onNameSpot = onNameSpot,
                        )

                        Spacer(Modifier.height(8.dp))

                        if (index < groups.size - 1) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Tap a place to edit it. Tap coordinates to make a " +
                            "place there.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },

        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * One visit: the place, when it started and ended, and the dwells behind
 * it. A single-dwell visit skips the list and offers its own coordinates
 * directly - the row above already says everything the list would.
 */
@Composable
private fun StopGroupEntry(
    position: Int,
    group: StopGroup,
    place: Place?,
    formatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    onOpenPlace: (Place) -> Unit,
    onNameSpot: (RideLocation) -> Unit,
) {

    // Coloured only when it actually opens the place editor - a stop
    // whose place never resolved is inert and says so by staying the
    // colour of ordinary text.
    Text(
        text = "$position. ${place?.name ?: "?"}",
        style = MaterialTheme.typography.titleMedium,
        color = if (place != null) tappableValueColor else Color.Unspecified,
        modifier = Modifier.clickable(
            enabled = place != null,
            onClick = { place?.let(onOpenPlace) }
        )
    )

    Text(
        text = "${formatter.format(Date(group.startTime))} – " +
            formatter.format(Date(group.endTime)),
        style = MaterialTheme.typography.bodyMedium
    )

    Text(
        text = "Duration: ${formatElapsedTime(group.elapsedSeconds)}",
        style = MaterialTheme.typography.bodySmall
    )

    if (group.isSingleStop) {
        StopCoordinates(
            location = group.stops.first().location,
            onNameSpot = onNameSpot,
        )
        return
    }

    Spacer(Modifier.height(4.dp))

    Text(
        text = "${group.stops.size} stops here:",
        style = MaterialTheme.typography.bodySmall
    )

    for (stop in group.stops) {

        val durationSeconds = ((stop.endTime - stop.startTime) / 1000).coerceAtLeast(0)

        Text(
            text = "${timeFormatter.format(Date(stop.startTime))} – " +
                "${timeFormatter.format(Date(stop.endTime))}  " +
                formatElapsedTime(durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp)
        )

        StopCoordinates(
            location = stop.location,
            onNameSpot = onNameSpot,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * A stop's own recorded point, and the way to turn it into a place. This
 * is the only screen that shows a stop's coordinates at all: they matter
 * exactly here, where the question is "which part of this place was
 * that?".
 */
@Composable
private fun StopCoordinates(
    location: RideLocation?,
    onNameSpot: (RideLocation) -> Unit,
    modifier: Modifier = Modifier,
) {

    if (location == null) {
        Text(
            text = "No location recorded",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier
        )
        return
    }

    Text(
        text = "%.5f, %.5f".format(location.latitude, location.longitude),
        style = MaterialTheme.typography.bodySmall,
        color = tappableValueColor,
        modifier = modifier.clickable { onNameSpot(location) }
    )
}
