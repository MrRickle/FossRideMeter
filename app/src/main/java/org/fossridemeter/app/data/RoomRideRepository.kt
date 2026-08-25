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
import android.util.Log
import kotlinx.coroutines.flow.Flow
import org.fossridemeter.app.model.RideRecord

class RoomRideRepository(
    context: Context
) : RideRepository {

    private val dao = AppDatabase.getInstance(context).rideDao()

    override val rides: Flow<List<RideRecord>>
        get() = dao.getAll()

    override suspend fun saveRide(ride: RideRecord) {
        Log.d("RideMeter", "RoomRideRepository saving ride: $ride")
        dao.insert(ride)
    }

    override suspend fun updateRide(ride: RideRecord) {
        dao.update(ride)
        Log.d("RoomRideRepository", "Updated ride ${ride.id}")
    }

    override suspend fun deleteRide(rideId: String) {
        dao.deleteById(rideId)
        Log.d("RoomRideRepository", "Deleted ride $rideId")
    }
}
