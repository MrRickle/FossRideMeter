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
import org.fossridemeter.app.model.Stop

@Dao
interface StopDao {

    @Insert
    suspend fun insert(stop: Stop)

    // Used for import: re-importing our own backup (matching id)
    // overwrites the existing row instead of erroring.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stop: Stop)

    @Update
    suspend fun update(stop: Stop)

    @Delete
    suspend fun delete(stop: Stop)

    @Query("SELECT * FROM stops ORDER BY sequence ASC")
    fun getAll(): Flow<List<Stop>>

    // One-shot list for export - a plain suspend call, not a Flow, since
    // export is a single snapshot rather than something to observe.
    @Query("SELECT * FROM stops ORDER BY sequence ASC")
    suspend fun getAllOnce(): List<Stop>

    @Query("SELECT * FROM stops WHERE rideId = :rideId ORDER BY sequence ASC")
    suspend fun getByRideId(rideId: String): List<Stop>

    @Query("SELECT * FROM stops WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: String): List<Stop>
}
