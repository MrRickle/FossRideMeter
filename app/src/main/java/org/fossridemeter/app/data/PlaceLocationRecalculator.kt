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
package org.fossridemeter.app.data

import org.fossridemeter.app.model.RideLocation

/**
 * Keeps a Place's latitude/longitude equal to the average of every ride
 * and stop linked to it. The user never enters a place's location
 * directly - only its name.
 *
 * Called after a ride (and its stops) is actually saved, not during
 * PlaceResolver's resolve step - the new points need to be in the
 * database before they can be counted, and resolution happens before
 * the save.
 *
 * Does a full recompute from source rows each time rather than keeping a
 * running average, so there's no incremental-average state to drift out
 * of sync - correct by construction on every call, at the cost of one
 * extra query. Fine at personal-app scale (rides/stops linked to any one
 * place will realistically be dozens, not thousands).
 */
class PlaceLocationRecalculator(
    private val rideDao: RideDao,
    private val stopDao: StopDao,
    private val placeDao: PlaceDao,
) {

    suspend fun recompute(placeId: String) {
        val place = placeDao.getById(placeId) ?: return

        // Locked places are manually curated (including via copy/paste
        // from a map app) - auto-averaging would silently drag a
        // deliberately set location back toward the mean the next time a
        // ride links here. Naming alone doesn't lock a place; only an
        // actual edit to its location does. Unlocked places (named or
        // not) keep converging as more rides/stops link to them.
        if (place.locationLocked) return

        val linkedRides = rideDao.getByPlaceId(placeId)
        val linkedStops = stopDao.getByPlaceId(placeId)

        val points = mutableListOf<RideLocation>()
        for (ride in linkedRides) {
            if (ride.startPlaceId == placeId) {
                ride.startLocation?.let { points.add(it) }
            }
            if (ride.endPlaceId == placeId) {
                ride.endLocation?.let { points.add(it) }
            }
        }
        for (stop in linkedStops) {
            val location = stop.location
            if (location != null) {
                points.add(location)
            }
        }

        if (points.isEmpty()) return

        val avgLatitude = points.sumOf { it.latitude } / points.size
        val avgLongitude = points.sumOf { it.longitude } / points.size

        placeDao.update(
            place.copy(
                latitude = avgLatitude,
                longitude = avgLongitude,
            )
        )
    }
}
