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
package org.fossridemeter.app.util

/**
 * Standard base32 geohash encoding. Used only as a readable, deterministic
 * placeholder name/dedup key for not-yet-named [org.fossridemeter.app.model.Place]
 * rows - not for spatial search, so no decode function or bounding-box
 * lookup is needed here.
 *
 * Precision guide (cell size per digit count):
 *   7 -> ~76m x 152m   8 -> ~19m x 19m   9 -> ~4.8m x 4.8m   10 -> ~1.2m x 0.6m
 */
object GeohashUtil {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * Default precision of 8 (~19m cells) sits close to typical consumer
     * GPS noise (3-10m, worse near buildings), so a repeat visit to the
     * same unnamed spot usually lands in the same cell without over-
     * splitting nearby houses. Bump to 9 if houses are colliding in
     * practice; duplicate unnamed Place rows from GPS jitter are expected
     * and cheap to merge later either way.
     */
    fun encode(
        latitude: Double,
        longitude: Double,
        precision: Int = 8
    ): String {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        val hash = StringBuilder(precision)
        var isEvenBit = true
        var bit = 0
        var charBits = 0

        while (hash.length < precision) {
            if (isEvenBit) {
                val mid = (lngMin + lngMax) / 2
                if (longitude >= mid) {
                    charBits = charBits or (1 shl (4 - bit))
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude >= mid) {
                    charBits = charBits or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEvenBit = !isEvenBit

            if (bit < 4) {
                bit++
            } else {
                hash.append(BASE32[charBits])
                bit = 0
                charBits = 0
            }
        }

        return hash.toString()
    }
}
