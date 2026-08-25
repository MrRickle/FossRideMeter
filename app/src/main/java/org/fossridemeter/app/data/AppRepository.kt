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

import android.content.Context

object AppRepository {

    private var repository: RoomRideRepository? = null
    private var placeResolver: PlaceResolver? = null
    private var placeLocationRecalculator: PlaceLocationRecalculator? = null
    private var placeBoundaryEnforcer: PlaceBoundaryEnforcer? = null

    fun get(context: Context): RoomRideRepository {

        if (repository == null) {
            repository = RoomRideRepository(
                context.applicationContext
            )
        }

        return repository!!
    }

    fun getPlaceResolver(context: Context): PlaceResolver {

        if (placeResolver == null) {
            placeResolver = PlaceResolver(
                AppDatabase.getInstance(context.applicationContext).placeDao()
            )
        }

        return placeResolver!!
    }

    fun getPlaceDao(context: Context): PlaceDao {
        return AppDatabase.getInstance(context.applicationContext).placeDao()
    }

    fun getStopDao(context: Context): StopDao {
        return AppDatabase.getInstance(context.applicationContext).stopDao()
    }

    // Used for bulk export/import (RidesViewModel) - RideMeter's own
    // read/write traffic still goes through the RideRepository interface
    // returned by get() above, not through this.
    fun getRideDao(context: Context): RideDao {
        return AppDatabase.getInstance(context.applicationContext).rideDao()
    }

    fun getPlaceLocationRecalculator(context: Context): PlaceLocationRecalculator {

        if (placeLocationRecalculator == null) {
            val db = AppDatabase.getInstance(context.applicationContext)
            placeLocationRecalculator = PlaceLocationRecalculator(
                rideDao = db.rideDao(),
                stopDao = db.stopDao(),
                placeDao = db.placeDao(),
            )
        }

        return placeLocationRecalculator!!
    }

    fun getPlaceBoundaryEnforcer(context: Context): PlaceBoundaryEnforcer {

        if (placeBoundaryEnforcer == null) {
            val db = AppDatabase.getInstance(context.applicationContext)
            placeBoundaryEnforcer = PlaceBoundaryEnforcer(
                rideDao = db.rideDao(),
                stopDao = db.stopDao(),
                placeDao = db.placeDao(),
                placeResolver = getPlaceResolver(context),
                placeLocationRecalculator = getPlaceLocationRecalculator(context),
            )
        }

        return placeBoundaryEnforcer!!
    }
}
