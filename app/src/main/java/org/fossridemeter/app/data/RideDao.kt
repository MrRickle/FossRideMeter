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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.fossridemeter.app.model.RideRecord

@Dao
interface RideDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ride: RideRecord)

    // Used for import: re-importing our own backup (matching id)
    // overwrites the existing row instead of aborting.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ride: RideRecord)

    @Update
    suspend fun update(ride: RideRecord)

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteById(rideId: String)

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAll(): Flow<List<RideRecord>>

    // One-shot list for export - a plain suspend call, not a Flow, since
    // export is a single snapshot rather than something to observe.
    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    suspend fun getAllOnce(): List<RideRecord>

    @Query("SELECT * FROM rides WHERE startPlaceId = :placeId OR endPlaceId = :placeId")
    suspend fun getByPlaceId(placeId: String): List<RideRecord>

    // Used to recover a ride that was still being metered when the
    // process died - see LiveRideStore.
    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getById(rideId: String): RideRecord?
}
