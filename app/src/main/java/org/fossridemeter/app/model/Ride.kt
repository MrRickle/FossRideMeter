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
package org.fossridemeter.app.model

data class Ride(
    val id: String = "INVALID",
    val name: String = "",
    val manualAmount: Double? = null,
    val status: RideStatus = RideStatus.READY,
    val meters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    // Of those seconds, how many were spent at a stop. RideMeter keeps
    // this from the stops it has recorded plus the dwell under way, and
    // AmountCalculator bills them at Settings.stoppedHourlyRate instead
    // of hourlyRate.
    val stoppedSeconds: Long = 0,
    val distanceAmount: Double = 0.0,
    val timeAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val startLocation: RideLocation? = null,
    val endLocation: RideLocation? = null,
    // Resolved as each end becomes known. startPlaceId/startPlaceName as
    // soon as the first GPS fix comes in during tracking. The end pair is
    // a *working* answer while the ride is live - it tracks the most
    // recent detected stop, the last place we know the vehicle actually
    // sat at - and is replaced with a real resolve of wherever the ride
    // is when the user confirms the save. The name is carried alongside
    // the id so the live screen can display it without needing a places
    // lookup of its own.
    val startPlaceId: String? = null,
    val startPlaceName: String? = null,
    val endPlaceId: String? = null,
    val endPlaceName: String? = null,
    // The stops detected so far, in order, for live display: their place
    // ids and the names those places had when each stop was recorded.
    // The Stop rows themselves (location, timing, sequence) are already
    // in the database - written as each stop was detected - and aren't
    // carried here.
    //
    // The id is what the display resolves through, so renaming a place
    // mid-ride renames it on the live screen too; the name is the
    // fallback for a place that has since been deleted. Both lists are
    // index-aligned and must be edited together.
    val stopPlaceIds: List<String> = emptyList(),
    val stopPlaceNames: List<String> = emptyList(),
) {
    companion object {
        const val INVALID_ID = "INVALID"
    }
}

fun Ride.toRecord(
    settings: Settings,
    amount: Double,
): RideRecord {
    require(id != Ride.INVALID_ID) {
        "Attempted to save ride without a valid ID"
    }
    return RideRecord(
        id = id,
        name = name,
        startTime = startTime,
        // Still running - fall back to startTime so the row's
        // non-nullable endTime always has a valid value. Filled in for
        // real when RideMeter pauses or saves the ride.
        endTime = endTime ?: startTime,
        startLocation = startLocation,
        endLocation = endLocation,
        startPlaceId = startPlaceId,
        endPlaceId = endPlaceId,
        meters = meters,
        elapsedSeconds = elapsedSeconds,
        stoppedSeconds = stoppedSeconds,
        calculatedAmount = amount,
        manualAmount = manualAmount,
        perMeterRate = settings.perMeterRate,
        hourlyRate = settings.hourlyRate,
        stoppedHourlyRate = settings.stoppedHourlyRate,
        baseAmount = settings.baseAmount,
        minimumAmount = settings.minimumAmount,
        distanceProvider = settings.distanceProvider,
    )
}
