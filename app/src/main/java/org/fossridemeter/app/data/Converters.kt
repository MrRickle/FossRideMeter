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

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import org.fossridemeter.app.model.DistanceProviderType
import org.fossridemeter.app.model.RideLocation

/**
 * Room can only store primitive/String columns directly, so RideLocation
 * (a nested object) is stored as a JSON string and DistanceProviderType
 * (an enum) is stored as its name. Using kotlinx.serialization here means
 * we don't need to hand-write field-by-field mapping for RideLocation,
 * and it stays in sync automatically if fields are added to it later.
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun fromRideLocation(location: RideLocation?): String? {
        return location?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toRideLocation(value: String?): RideLocation? {
        if (value.isNullOrEmpty()) return null
        return json.decodeFromString(RideLocation.serializer(), value)
    }

    @TypeConverter
    fun fromDistanceProviderType(type: DistanceProviderType): String {
        return type.name
    }

    @TypeConverter
    fun toDistanceProviderType(value: String): DistanceProviderType {
        return DistanceProviderType.valueOf(value)
    }
}
