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

import org.fossridemeter.app.model.DistanceProviderType
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.util.composedName
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Stop

/**
 * One ride as "Ride Information" needs it, whether that ride is still
 * running or was saved months ago.
 *
 * The two exist in different forms - `Ride` in memory with live settings,
 * `RideRecord` as a row carrying the rates it was metered at - and the
 * section used to be written twice, once per form, which is precisely how
 * the two drifted apart: the live one gained no place links and the saved
 * one gained no name row. Mapping both into this instead means there is
 * one section, and "identical in both screens" is structural rather than
 * something to keep remembering.
 *
 * Converting a live ride with `Ride.toRecord()` would have been the
 * obvious shortcut and is wrong twice over: it throws on a ride that has
 * no id yet, and `RideRecord.endTime` is non-nullable, so a ride still
 * running would report an end time equal to its start time rather than
 * none at all. [endTime] is nullable here for that reason.
 */
data class RideInfo(
    val name: String,
    val manualAmount: Double?,
    val calculatedAmount: Double,
    val meters: Double,
    val elapsedSeconds: Long,
    // Of those, the seconds spent at stops, and the rate they were billed
    // at. The rate is null on a ride recorded before there was one.
    val stoppedSeconds: Long,
    val stoppedHourlyRate: Double?,
    val startTime: Long,
    val endTime: Long?,
    val startPlaceId: String?,
    val startPlaceName: String?,
    val endPlaceId: String?,
    val endPlaceName: String?,
    // Index-aligned with stopPlaceNames. The id is what the screen
    // resolves a current name through; the name is what it falls back to
    // when the place is gone.
    val stopPlaceIds: List<String?>,
    val stopPlaceNames: List<String?>,
    val startLocation: RideLocation?,
    val endLocation: RideLocation?,
    val perMeterRate: Double,
    val hourlyRate: Double,
    val baseAmount: Double,
    val minimumAmount: Double,
    val distanceProvider: DistanceProviderType,
)

/**
 * A ride in progress. Its rates come from current settings, which is
 * sound because Settings is locked for the whole of any live ride - the
 * rates on screen are necessarily the ones being metered.
 */
fun Ride.toRideInfo(settings: Settings): RideInfo =
    RideInfo(
        name = name,
        manualAmount = manualAmount,
        calculatedAmount = totalAmount,
        meters = meters,
        elapsedSeconds = elapsedSeconds,
        stoppedSeconds = stoppedSeconds,
        stoppedHourlyRate = settings.stoppedHourlyRate,
        startTime = startTime,
        endTime = endTime,
        startPlaceId = startPlaceId,
        startPlaceName = startPlaceName,
        endPlaceId = endPlaceId,
        endPlaceName = endPlaceName,
        // Carried on the ride itself as it goes - RideMeter appends each
        // stop's place as it detects it. Collapsed by *id*, the same way
        // a saved ride's stops are grouped, so sitting twice in the same
        // lot reads as one stop live and still reads as one afterwards -
        // and two different places that happen to share a name stay two.
        stopPlaceIds = collapsedStopIndices().map { stopPlaceIds.getOrNull(it) },
        stopPlaceNames = collapsedStopIndices().map { stopPlaceNames.getOrNull(it) },
        startLocation = startLocation,
        endLocation = endLocation,
        perMeterRate = settings.perMeterRate,
        hourlyRate = settings.hourlyRate,
        baseAmount = settings.baseAmount,
        minimumAmount = settings.minimumAmount,
        distanceProvider = settings.distanceProvider,
    )

/**
 * A saved ride. Rates come off the row, not from settings: the rates a
 * ride was metered at belong to that ride, and settings may have changed
 * many times since.
 */
fun RideRecord.toRideInfo(
    places: Map<String, Place>,
    stops: List<Stop>,
    placeNameDepth: Int = 1,
): RideInfo =
    RideInfo(
        name = name,
        manualAmount = manualAmount,
        calculatedAmount = calculatedAmount,
        meters = meters,
        elapsedSeconds = elapsedSeconds,
        stoppedSeconds = stoppedSeconds,
        stoppedHourlyRate = stoppedHourlyRate,
        startTime = startTime,
        endTime = endTime,
        startPlaceId = startPlaceId,
        startPlaceName = places.composedName(startPlaceId, placeNameDepth),
        endPlaceId = endPlaceId,
        endPlaceName = places.composedName(endPlaceId, placeNameDepth),
        // Resolved through the places map rather than stored on the row -
        // a stop keeps only its place id, so a renamed place shows its
        // new name here without rewriting any ride. Consecutive stops at
        // one place are one entry here (see StopGroup); the rows behind
        // them are untouched.
        stopPlaceIds = stops.groupConsecutiveByPlace().map { it.placeId },
        stopPlaceNames = stops
            .groupConsecutiveByPlace()
            .map { group -> places.composedName(group.placeId, placeNameDepth) },
        startLocation = startLocation,
        endLocation = endLocation,
        perMeterRate = perMeterRate,
        hourlyRate = hourlyRate,
        baseAmount = baseAmount,
        minimumAmount = minimumAmount,
        distanceProvider = distanceProvider,
    )

/**
 * The indices of a live ride's stops that survive collapsing runs of
 * consecutive stops at one place into a single entry.
 *
 * Taken as indices rather than as a filtered list of names, because the
 * ids and the names have to be collapsed identically or they stop
 * describing the same stops.
 */
private fun Ride.collapsedStopIndices(): List<Int> =
    stopPlaceIds.indices.filter { index ->
        index == 0 || stopPlaceIds[index] != stopPlaceIds[index - 1]
    }
