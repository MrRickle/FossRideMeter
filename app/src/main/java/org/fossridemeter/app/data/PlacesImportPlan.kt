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

import org.fossridemeter.app.model.Place
import org.fossridemeter.app.util.DistanceUtil
import kotlin.math.abs

/**
 * Two centres this close are the same place. Copied from one phone to
 * another, a place that both phones have averaged from their own rides
 * won't sit on the same coordinates, and GPS scatter alone is several
 * metres - so exact equality would never match and every import would
 * duplicate "Home". Kept well under any sensible radius, so a shop drawn
 * inside a mall is still a different place from the mall.
 */
const val SAME_PLACE_METERS = 10.0

/** Radii closer than this are the same radius - the editor rounds to a
 * whole foot or metre, and a round trip through feet doesn't come back
 * to the exact double. */
private const val SAME_RADIUS_METERS = 0.5

/** A named place already on this phone, and the one in the file at the
 * same spot with a different radius. Which radius wins is the user's
 * call. */
data class RadiusConflict(
    val existing: Place,
    val incoming: Place,
)

data class PlacesImportPlan(
    /** Upserted as they are: new here, or the same id moved. */
    val toWrite: List<Place>,
    /** Held for the user to decide. */
    val conflicts: List<RadiusConflict>,
    /** Already here with the same radius - nothing to do. */
    val alreadyHere: Int,
    /** Unnamed placeholders in the file, which belong to the other
     * phone's ride history and mean nothing on this one. */
    val skippedUnnamed: Int,
)

/**
 * Sorts a file's places against the ones already in the database.
 *
 * Only named places are imported. A named place in the file matches a
 * named place here by id first (the same place, imported before), then
 * by the nearest centre within [SAME_PLACE_METERS]. A match with the same
 * radius is skipped and one with a different radius becomes a
 * [RadiusConflict]; either way the existing row keeps its id, so the
 * rides and stops already linked to it stay linked.
 *
 * Unnamed places here are never matched: they're placeholders, and
 * PlaceBoundaryEnforcer absorbs them once the imported place is written.
 */
fun planPlacesImport(
    incoming: List<Place>,
    existing: List<Place>,
): PlacesImportPlan {

    val named = existing.filter { it.isNamed }
    val byId = named.associateBy { it.id }

    val toWrite = mutableListOf<Place>()
    val conflicts = mutableListOf<RadiusConflict>()
    var alreadyHere = 0
    var skippedUnnamed = 0

    for (place in incoming) {
        if (!place.isNamed) {
            skippedUnnamed++
            continue
        }

        val match = byId[place.id]?.takeIf { metersBetween(it, place) <= SAME_PLACE_METERS }
            ?: named
                .map { it to metersBetween(it, place) }
                .filter { (_, meters) -> meters <= SAME_PLACE_METERS }
                .minByOrNull { (_, meters) -> meters }
                ?.first

        when {
            match == null -> toWrite += place
            abs(match.radiusMeters - place.radiusMeters) < SAME_RADIUS_METERS -> alreadyHere++
            else -> conflicts += RadiusConflict(existing = match, incoming = place)
        }
    }

    return PlacesImportPlan(toWrite, conflicts, alreadyHere, skippedUnnamed)
}

private fun metersBetween(a: Place, b: Place): Double =
    DistanceUtil.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
