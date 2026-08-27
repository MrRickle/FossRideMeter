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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.util.displayAmount
import org.fossridemeter.app.util.displayDistance
import org.fossridemeter.app.util.displayDistanceRate
import org.fossridemeter.app.util.distanceUnit
import java.text.SimpleDateFormat
import java.util.Date

/**
 * "Ride Information" - one section, used by the live screen and by a
 * saved ride's detail alike. Callers map whichever form of ride they hold
 * into [RideInfo]; see that file for why the two aren't converted into
 * one another directly.
 *
 * Every row that can lead somewhere does, by being tapped:
 *
 * * **Ride Name** and **Amount** open a one-field editor, when the caller
 *   supplies a way to write them back. That is all a ride has that can be
 *   edited, so there is no separate edit mode for one - what you see is
 *   what you edit.
 * * **Start Place** / **End Place** open the place editor, when the id
 *   resolves to a place.
 * * **Stops** opens the stops list.
 *
 * A row whose callback is absent, or whose place hasn't resolved, is
 * simply not clickable.
 */
@Composable
fun RideInfoSection(
    info: RideInfo,
    settings: Settings,
    places: Map<String, Place> = emptyMap(),
    onOpenStops: () -> Unit = {},
    onOpenPlace: (Place) -> Unit = {},
    onNameChanged: ((String) -> Unit)? = null,
    onAmountChanged: ((Double?) -> Unit)? = null,
) {

    var editingName by remember { mutableStateOf(false) }
    var editingAmount by remember { mutableStateOf(false) }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Ride Information",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(8.dp))

    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) {
        SimpleDateFormat("MMM d, yyyy  h:mm a", locale)
    }

    InfoRow(
        "Ride Name",
        info.name.ifBlank { "-" },
        onClick = if (onNameChanged != null) ({ editingName = true }) else null
    )

    InfoRow(
        "Amount",
        displayAmount(info.manualAmount ?: info.calculatedAmount),
        onClick = if (onAmountChanged != null) ({ editingAmount = true }) else null
    )

    InfoRow(
        "Distance",
        displayDistance(info.meters, settings.measurementSystem.distanceUnit)
    )

    // The place is what the editor needs, but the name is what the ride
    // carries - so a place that hasn't resolved still reads correctly,
    // it just isn't clickable.
    val startPlace = info.startPlaceId?.let(places::get)
    InfoRow(
        "Start Place",
        startPlace?.name ?: info.startPlaceName ?: "-",
        onClick = startPlace?.let { place -> { onOpenPlace(place) } }
    )

    val endPlace = info.endPlaceId?.let(places::get)

    // Each stop's name comes from the places map by id, exactly as the
    // start and end places above do, and falls back to the name the ride
    // carries only when the place itself is gone. Reading the carried
    // name first is what left a running ride's stops showing the old
    // name after a rename while the two ends of the same line updated.
    val stopNames = info.stopPlaceNames.mapIndexed { index, carried ->
        info.stopPlaceIds.getOrNull(index)?.let(places::get)?.name ?: carried
    }

    InfoRow(
        "Stops",
        if (stopNames.isEmpty()) {
            "-"
        } else {
            buildPlacesSummary(
                startPlaceName = startPlace?.name ?: info.startPlaceName,
                stopPlaceNames = stopNames,
                endPlaceName = endPlace?.name ?: info.endPlaceName,
            )
        },
        onClick = onOpenStops
    )

    InfoRow(
        "End Place",
        endPlace?.name ?: info.endPlaceName ?: "-",
        onClick = endPlace?.let { place -> { onOpenPlace(place) } }
    )

    InfoRow("Duration", formatElapsedTime(info.elapsedSeconds))

    // Only when there is some. A ride that never stopped shouldn't carry
    // a row of zeroes explaining a charge that didn't happen.
    if (info.stoppedSeconds > 0) {
        InfoRow("Stopped", formatElapsedTime(info.stoppedSeconds))
    }

    InfoRow(
        "Start Time",
        if (info.startTime > 0L) formatter.format(Date(info.startTime)) else "-"
    )

    InfoRow(
        "End Time",
        info.endTime?.let { formatter.format(Date(it)) } ?: "-"
    )

    InfoRow(
        "Rate",
        displayDistanceRate(info.perMeterRate, settings.measurementSystem.distanceUnit)
    )

    InfoRow("Per Hour", displayAmount(info.hourlyRate))

    info.stoppedHourlyRate?.let { rate ->
        InfoRow("Per Hour While Stopped", displayAmount(rate))
    }

    InfoRow("Base", displayAmount(info.baseAmount))

    InfoRow("Minimum", displayAmount(info.minimumAmount))

    // What the meter made it, before any override - the figure the
    // Amount row replaces when one is set.
    InfoRow("Original Amount", displayAmount(info.calculatedAmount))

    InfoRow(
        "Start Location",
        info.startLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "Unknown"
    )

    InfoRow(
        "End Location",
        info.endLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "Unknown"
    )

    InfoRow("GPS Provider", info.distanceProvider.displayName)

    if (editingName && onNameChanged != null) {
        FieldEditDialog(
            title = "Ride Name",
            initialValue = info.name,
            onConfirm = { onNameChanged(it) },
            onDismiss = { editingName = false }
        )
    }

    if (editingAmount && onAmountChanged != null) {
        FieldEditDialog(
            title = "Override Amount",
            initialValue = info.manualAmount?.let { "%.2f".format(it) } ?: "",
            keyboardType = KeyboardType.Decimal,
            supportingText = "Leave blank to use the metered amount",
            // Blank clears the override rather than setting zero, which is
            // the only way back to the metered figure once one is set.
            onConfirm = { text ->
                if (text.isBlank()) {
                    onAmountChanged(null)
                } else {
                    text.toDoubleOrNull()?.let { onAmountChanged(it) }
                }
            },
            onDismiss = { editingAmount = false }
        )
    }
}

/**
 * One label-and-value row, tappable when [onClick] is given.
 *
 * Clickability is a parameter rather than something the caller layers on
 * with a modifier, so that a row cannot be made to lead somewhere without
 * also being coloured as though it does. Every row here looks like a
 * printout otherwise, and there is nothing else on screen to tell the
 * ones that open an editor from the ones that only report a number.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {

    Column(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) tappableValueColor else Color.Unspecified
        )

        Spacer(
            Modifier.height(6.dp)
        )
    }
}

/**
 * "Home -> Clinic -> Walmart -> Home" - the whole ride as one line. "?"
 * stands in for any leg whose place hasn't resolved to a name (shouldn't
 * normally happen once PlaceResolver has run, but GPS can fail to get a
 * fix at all for a given point).
 */
fun buildPlacesSummary(
    startPlaceName: String?,
    stopPlaceNames: List<String?>,
    endPlaceName: String?,
): String {
    val legs = listOf(startPlaceName) + stopPlaceNames + listOf(endPlaceName)
    return legs.joinToString(" → ") { it ?: "?" }
}

fun formatElapsedTime(
    seconds: Long
): String {

    val hours =
        seconds / 3600

    val minutes =
        (seconds % 3600) / 60

    val secs =
        seconds % 60

    return "%02d:%02d:%02d".format(
        hours,
        minutes,
        secs
    )
}
