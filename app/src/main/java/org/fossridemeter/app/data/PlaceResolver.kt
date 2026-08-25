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

import org.fossridemeter.app.model.Place
import org.fossridemeter.app.util.DistanceUtil
import org.fossridemeter.app.util.GeohashUtil

/**
 * Resolves a raw GPS point to a Place row. Called for a ride's start point
 * as soon as the first GPS fix arrives, for each detected stop as it's
 * finalized, for the end point at finish(), and again by
 * PlaceBoundaryEnforcer for points pushed outside an edited place.
 *
 * Matching is a single rule for every place, named or not: distance <=
 * that place's own radiusMeters. A manually-widened "whole Walmart lot"
 * place matches anywhere inside it the same way a freshly auto-created
 * placeholder matches within its small default radius. The geohash plays
 * no role in matching - it only supplies the initial readable name text
 * when a new place gets created.
 *
 * Places overlap, so the rule can produce several matches and something
 * has to choose between them. A named place wins over an unnamed
 * placeholder - the user named it, the app invented the other - and
 * between two of a kind the tighter radius wins, then the nearer centre.
 * That reads as "the most specific place the user has actually named",
 * and it is deterministic; the previous first-row-wins depended on table
 * order, so a leftover placeholder could shadow the "Home Depot" drawn
 * around it forever.
 */
/**
 * Radius a place gets when the user drops one on a point by hand, from
 * a stop in the stops list. Deliberately far tighter than the 200 ft an
 * auto-created placeholder gets: this one is being drawn *inside*
 * something bigger - the lumber aisle within the Home Depot - and a
 * generous radius would swallow the neighbouring aisles it exists to tell
 * apart. Widen it in the editor when the spot really is that big.
 */
const val HAND_PLACED_RADIUS_METERS: Double = 24.384 // 80 ft

/**
 * A new place at a point, unsaved and unnamed - the row PlaceResolver
 * writes when nothing matches, and the row the stops list hands to the
 * place editor when the user names a spot. Same shape either way, so a
 * place named by hand is not a second kind of place.
 */
fun placeAt(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    geohashPrecision: Int = 8,
): Place {
    val hash = GeohashUtil.encode(latitude, longitude, geohashPrecision)
    return Place(
        name = hash,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        geohash = hash,
        isNamed = false,
    )
}

class PlaceResolver(
    private val placeDao: PlaceDao,
    private val geohashPrecision: Int = 8,
    // 200 ft. Stored in meters like everything else - the app is SI
    // internally and converts only for display. Wide enough that a place
    // covers the whole driveway or lot rather than one parking space,
    // which matters most to PlaceWatcher: a radius smaller than a coarse
    // fix's error can't be resolved without spending GPS on it.
    private val unnamedPlaceholderRadiusMeters: Double = 60.96,
) {

    suspend fun resolvePlace(latitude: Double, longitude: Double): Place {
        fun metersTo(place: Place) = DistanceUtil.haversineMeters(
            latitude, longitude,
            place.latitude, place.longitude
        )

        val match = placeDao.getAllOnce()
            .filter { place -> metersTo(place) <= place.radiusMeters }
            .minWithOrNull(
                compareBy<Place>(
                    { if (it.isNamed) 0 else 1 },
                    { it.radiusMeters },
                    { metersTo(it) },
                )
            )

        if (match != null) return match

        val newPlace = placeAt(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = unnamedPlaceholderRadiusMeters,
            geohashPrecision = geohashPrecision,
        )
        placeDao.insert(newPlace)
        return newPlace
    }
}
