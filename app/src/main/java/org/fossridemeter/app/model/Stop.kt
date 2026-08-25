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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A place visited during a ride - the clinic, Walmart, etc. Same location
 * type as a ride's start/end (RideLocation), resolved to a Place the same
 * way (via PlaceResolver) and factored into that Place's averaged
 * location the same way (via PlaceLocationRecalculator).
 *
 * `sequence` orders stops within a ride (0, 1, 2, ...) rather than relying
 * on row insertion order, so reordering is possible later without
 * renumbering being implicit/fragile.
 *
 * Duration isn't stored - it's startTime/endTime, computed when displayed.
 */
@Serializable
@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = RideRecord::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["rideId"]),
        Index(value = ["placeId"]),
    ]
)
data class Stop(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val rideId: String,

    val sequence: Int,

    val startTime: Long,
    val endTime: Long,

    // Actual measured point - kept forever, same as RideRecord.startLocation/
    // endLocation. Never changes even if the linked Place's location does.
    val location: RideLocation?,

    // Resolved Place for this stop's location, same resolution path as a
    // ride's start/end. Null only if location itself is null.
    val placeId: String? = null,
)
