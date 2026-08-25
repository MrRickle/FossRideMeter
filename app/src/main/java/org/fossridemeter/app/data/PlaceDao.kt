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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.fossridemeter.app.model.Place

@Dao
interface PlaceDao {

    @Insert
    suspend fun insert(place: Place)

    // Used for import: a re-imported place (matching id, e.g. from our
    // own JSON backup) overwrites the existing row instead of erroring;
    // a genuinely new place (fresh id from a CSV/GPX import) just inserts.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: Place)

    @Update
    suspend fun update(place: Place)

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): Place?

    // One-shot list for matching (PlaceResolver) and recompute
    // (PlaceLocationRecalculator) - a plain suspend call, not a Flow,
    // since both run as a single step mid-save rather than observing.
    /**
     * Rides and stops keep a plain `placeId` column rather than a foreign
     * key, so deleting a place can't take ride history with it. The rows
     * left pointing at nothing are then re-resolved by
     * PlaceBoundaryEnforcer.reattachDeleted() - which is why this only
     * deletes, and callers do that second step themselves.
     */
    @Query("DELETE FROM places WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM places")
    suspend fun getAllOnce(): List<Place>

    @Query("SELECT * FROM places ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Place>>
}
