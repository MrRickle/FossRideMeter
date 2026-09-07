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

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Stop

private val ridesBackupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

// A Stop is meaningless without its ride, so rides and their stops travel
// together in one bundle even though Rides and Places are otherwise
// exported/imported completely independently (rides only ever reference
// places by id - startPlaceId/endPlaceId - which resolve to "-" in the UI
// if the place isn't present, so there's no hard ordering requirement
// between a Rides import and a Places import).
@Serializable
data class RideBackupEntry(
    val ride: RideRecord,
    val stops: List<Stop> = emptyList(),
)

@Serializable
data class RidesBackup(
    val version: Int = 1,
    val rides: List<RideBackupEntry>,
)

fun exportRidesJson(
    rides: List<RideRecord>,
    stopsByRide: Map<String, List<Stop>>,
): String {
    val entries = rides.map { ride ->
        RideBackupEntry(
            ride = ride,
            stops = stopsByRide[ride.id].orEmpty(),
        )
    }
    return ridesBackupJson.encodeToString(RidesBackup(rides = entries))
}

fun parseRidesJson(text: String): List<RideBackupEntry> {
    return ridesBackupJson.decodeFromString<RidesBackup>(text).rides
}
