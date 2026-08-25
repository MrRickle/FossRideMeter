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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A saved location a ride can start or end at.
 *
 * `radiusMeters` is the real coverage area (can be as large as a whole
 * parking lot once the user has deliberately widened it) - matching is
 * always by distance <= radiusMeters, for named and unnamed places alike.
 * `geohash` is purely a readable placeholder for [name] on creation; it
 * plays no role in matching once the place exists.
 */
@Serializable
@Entity(
    tableName = "places",
    indices = [Index(value = ["geohash"])]
)
data class Place(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Starts out equal to the geohash (e.g. "9q8yyk9p") as a readable,
    // typeable placeholder. Overwritten when the user gives it a real name.
    val name: String,

    // Kept up to date by PlaceLocationRecalculator as an average of every
    // ride (and, later, stop) linked to this place - never entered
    // directly by the user.
    val latitude: Double,
    val longitude: Double,

    // Coverage radius in meters. Small default for unnamed placeholders;
    // the user can widen this to cover a whole parking lot once they've
    // named the place and are deliberately setting its extent.
    val radiusMeters: Double,

    val geohash: String,

    // False until the user gives it a real name. Display-only - doesn't
    // gate matching or averaging (see locationLocked for that).
    val isNamed: Boolean = false,

    // True only once the user has directly edited this place's location
    // (typed/pasted coordinates in the editor). Naming a place does NOT
    // set this - a named place with an unlocked location still keeps
    // converging via averaging as more rides link to it. Once locked,
    // PlaceLocationRecalculator leaves it alone permanently.
    val locationLocked: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    // Groundwork for future automatic ride start/stop by location - not
    // acted on anywhere yet, just stored intent per place.
    val autoStart: Boolean = false,
    val autoSave: Boolean = false,
)
