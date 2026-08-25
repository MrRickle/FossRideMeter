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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import org.fossridemeter.app.data.Converters
import java.util.UUID

@Serializable
@Entity(tableName = "rides")
@TypeConverters(Converters::class)
data class RideRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",

    val startTime: Long,
    val endTime: Long,

    val startLocation: RideLocation?,
    val endLocation: RideLocation?,

    // Links to the resolved Place row for the start/end point, set on save
    // by PlaceResolver. Null until resolution runs; startLocation/
    // endLocation above remain the actual measured GPS point regardless of
    // which Place (if any) it got matched to.
    val startPlaceId: String? = null,
    val endPlaceId: String? = null,

    val meters: Double,
    val elapsedSeconds: Long,

    // How much of elapsedSeconds was spent at this ride's stops. Zero on
    // a ride recorded before the stopped rate existed, which is exactly
    // right: all of its time was billed at hourlyRate.
    @ColumnInfo(defaultValue = "0")
    val stoppedSeconds: Long = 0,

    val calculatedAmount: Double = 0.0,
    val manualAmount: Double? = null,
    val displayAmount: Double = manualAmount ?: calculatedAmount,

    val perMeterRate: Double,

    val hourlyRate: Double,

    // Null on a ride from before there was one, so its detail reads "-"
    // rather than claiming a rate of zero was in force.
    val stoppedHourlyRate: Double? = null,

    val baseAmount: Double,
    val minimumAmount: Double,

    val distanceProvider: DistanceProviderType,
)
