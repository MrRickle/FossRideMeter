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

import org.fossridemeter.app.model.Place

/**
 * A place's name with the places it sits inside, outermost first:
 * `LaCrosse|Home Depot|Lumber`.
 *
 * A place inside another is common and the app already resolves to the
 * tighter one - a stop in "Home Depot Windows" is that, not "Home
 * Depot". What it then displayed was the tighter name alone, which
 * throws away the half that says *which* one. "kwik trip" is a chain
 * with a branch in every town; "Lumber" is a department in every
 * hardware store.
 *
 * Until now that was worked around by hand: places were literally named
 * "HD Lumber" and "Aldi's Sparta", encoding the parent into the child
 * because nothing else would show it. Those can go back to being called
 * Lumber and Sparta.
 *
 * ## What counts as a parent
 *
 * The smallest **named** place whose circle contains this one's centre
 * and is larger than it. Smallest, so a department reads as belonging
 * to its store rather than to the city; larger, so two circles of the
 * same size can't each claim the other; named, because an unnamed
 * geohash placeholder is bookkeeping and says nothing.
 *
 * Walking outward from there gives the chain. It is display only -
 * nothing here is written to a place, and the editor always shows and
 * saves the real name.
 */
object PlaceNames {

    const val SEPARATOR = "|"

    /**
     * [depth] is how many levels to show, the place itself included: 1
     * is the bare name, 2 is `Home Depot|Lumber`, 4 reaches
     * `Washington|LaCrosse|Home Depot|Lumber`. Deeper than the chain
     * goes simply shows the whole chain.
     */
    fun composed(
        place: Place,
        places: Collection<Place>,
        depth: Int,
    ): String {

        if (depth <= 1) return place.name

        val chain = mutableListOf(place)
        var current = place

        // Bounded by depth, and by seen ids: a place cannot contain
        // itself, but bad radii could still describe a loop, and a loop
        // here would hang the list it is drawn in.
        val seen = mutableSetOf(place.id)

        while (chain.size < depth) {
            val parent = parentOf(current, places) ?: break
            if (!seen.add(parent.id)) break
            chain.add(parent)
            current = parent
        }

        return chain.reversed().joinToString(SEPARATOR) { it.name }
    }

    private fun parentOf(place: Place, places: Collection<Place>): Place? =
        places
            .filter { candidate ->
                candidate.id != place.id &&
                    candidate.isNamed &&
                    candidate.radiusMeters > place.radiusMeters &&
                    DistanceUtil.haversineMeters(
                        candidate.latitude, candidate.longitude,
                        place.latitude, place.longitude,
                    ) <= candidate.radiusMeters
            }
            .minByOrNull { it.radiusMeters }
}

/**
 * The composed name for a place id, for the many places that hold a map
 * of places and want the name to show.
 */
fun Map<String, Place>.composedName(placeId: String?, depth: Int): String? =
    placeId?.let(::get)?.let { PlaceNames.composed(it, values, depth) }
