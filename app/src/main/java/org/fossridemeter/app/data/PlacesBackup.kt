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

import android.util.Xml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.util.GeohashUtil
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.UUID

// Radius has no equivalent in any external format (GPX/CSV don't carry
// one) - imported places land with this default and the user can widen
// or narrow it afterward, same as a brand-new unnamed place.
private const val DEFAULT_IMPORTED_RADIUS_METERS = 50.0

private val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/** Wrapper (rather than a bare JSON array) so the format can gain fields
 * later without breaking old exports. */
@Serializable
data class PlacesBackup(
    val version: Int = 1,
    val places: List<Place>,
)

/** Our own native format - lossless round trip of every Place field,
 * including id, so re-importing the same file updates existing rows
 * instead of duplicating them. */
fun exportPlacesJson(places: List<Place>): String {
    return backupJson.encodeToString(PlacesBackup(places = places))
}

fun parsePlacesJson(text: String): List<Place> {
    return backupJson.decodeFromString<PlacesBackup>(text).places
}

/**
 * Generic CSV: name, latitude, longitude[, radius_meters]. A header row
 * is auto-detected (and skipped) by checking whether the 2nd/3rd columns
 * of the first row parse as numbers - if they don't, it's a header.
 */
fun parsePlacesCsv(text: String): List<Place> {

    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()

    val startIndex = if (looksLikeCsvHeader(lines.first())) 1 else 0

    return lines.drop(startIndex).mapNotNull { line ->
        val fields = splitCsvLine(line)
        if (fields.size < 3) return@mapNotNull null

        val name = fields[0]
        val lat = fields[1].toDoubleOrNull() ?: return@mapNotNull null
        val lon = fields[2].toDoubleOrNull() ?: return@mapNotNull null
        val radius = fields.getOrNull(3)?.toDoubleOrNull() ?: DEFAULT_IMPORTED_RADIUS_METERS

        newImportedPlace(
            name = name.ifBlank { GeohashUtil.encode(lat, lon) },
            latitude = lat,
            longitude = lon,
            radiusMeters = radius,
        )
    }
}

private fun looksLikeCsvHeader(line: String): Boolean {
    val fields = splitCsvLine(line)
    if (fields.size < 3) return true
    val latOk = fields[1].toDoubleOrNull() != null
    val lonOk = fields[2].toDoubleOrNull() != null
    return !(latOk && lonOk)
}

/** Minimal RFC-4180-ish splitter: handles quoted fields (so a place name
 * containing a comma doesn't break the row) without pulling in a CSV
 * library for three columns. */
private fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == ',' && !inQuotes -> {
                result.add(current.toString())
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    result.add(current.toString())
    return result.map { it.trim().trim('"') }
}

/**
 * GPX waypoints: <wpt lat="" lon=""><name>...</name></wpt>. This is the
 * native Favorites export format for OsmAnd/Organic Maps, and what
 * Google Takeout/My Maps data converts to as well.
 */
fun parsePlacesGpx(text: String): List<Place> {

    val places = mutableListOf<Place>()

    val parser: XmlPullParser = Xml.newPullParser()
    parser.setInput(StringReader(text))

    var eventType = parser.eventType
    var currentLat: Double? = null
    var currentLon: Double? = null
    var currentName: String? = null
    var inWaypoint = false
    var inName = false

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "wpt" -> {
                        inWaypoint = true
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        currentName = null
                    }
                    "name" -> if (inWaypoint) inName = true
                }
            }
            XmlPullParser.TEXT -> {
                if (inWaypoint && inName) {
                    currentName = (currentName ?: "") + parser.text
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "name" -> inName = false
                    "wpt" -> {
                        val lat = currentLat
                        val lon = currentLon
                        if (lat != null && lon != null) {
                            places.add(
                                newImportedPlace(
                                    name = currentName?.trim().takeUnless { it.isNullOrBlank() }
                                        ?: GeohashUtil.encode(lat, lon),
                                    latitude = lat,
                                    longitude = lon,
                                    radiusMeters = DEFAULT_IMPORTED_RADIUS_METERS,
                                )
                            )
                        }
                        inWaypoint = false
                    }
                }
            }
        }
        eventType = parser.next()
    }

    return places
}

/**
 * Picks a parser by file extension when there is one, otherwise sniffs
 * the content itself (JSON starts with '{', GPX/XML starts with '<',
 * anything else is treated as CSV).
 */
fun parsePlacesAuto(text: String, fileName: String?): List<Place> {
    val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return when (extension) {
        "json" -> parsePlacesJson(text)
        "csv" -> parsePlacesCsv(text)
        "gpx", "xml" -> parsePlacesGpx(text)
        else -> {
            val trimmed = text.trimStart()
            when {
                trimmed.startsWith("{") -> parsePlacesJson(text)
                trimmed.startsWith("<") -> parsePlacesGpx(text)
                else -> parsePlacesCsv(text)
            }
        }
    }
}

private fun newImportedPlace(
    name: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
): Place {
    return Place(
        id = UUID.randomUUID().toString(),
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        geohash = GeohashUtil.encode(latitude, longitude),
        isNamed = true,
        locationLocked = true,
        createdAt = System.currentTimeMillis(),
        autoStart = false,
        autoSave = false,
    )
}
