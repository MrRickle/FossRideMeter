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
package org.fossridemeter.app.ui

import org.fossridemeter.app.model.Stop

/**
 * Consecutive stops that resolved to the same place, shown as one stop.
 *
 * A visit to a big place is often several stops: park, sit, move to
 * another door, sit again. Each is a real dwell and each is worth keeping
 * - they are what a place drawn inside this one later resolves from - but
 * reading "Home Depot -> Home Depot -> Home Depot" tells the user
 * nothing. So the grouping is display-only: the rows are never merged,
 * their points and times stay exactly as recorded, and the moment a
 * tighter place claims one of them (see PlaceBoundaryEnforcer step 2) it
 * separates back out on its own.
 *
 * Only *consecutive* stops group. Leaving Home Depot, driving elsewhere,
 * and coming back is two visits and reads as two.
 *
 * An unresolved stop (no placeId) never groups, with another unresolved
 * stop least of all - "somewhere" and "somewhere else" are not the same
 * place, they are two absences of an answer.
 */
data class StopGroup(
    val placeId: String?,
    val stops: List<Stop>,
) {
    /** When the first of these stops began. */
    val startTime: Long get() = stops.first().startTime

    /** When the last of them ended. */
    val endTime: Long get() = stops.last().endTime

    /**
     * Wall-clock span, so it counts the minutes spent moving between the
     * grouped stops as well as the ones spent sitting still. That is the
     * honest answer to "how long were you at Home Depot"; the individual
     * dwells are still listed underneath for anyone who wants them.
     */
    val elapsedSeconds: Long
        get() = ((endTime - startTime) / 1000).coerceAtLeast(0)

    val isSingleStop: Boolean get() = stops.size == 1
}

/** Groups by [Stop.sequence] order, whatever order the list arrives in. */
fun List<Stop>.groupConsecutiveByPlace(): List<StopGroup> {

    val grouped = mutableListOf<MutableList<Stop>>()

    for (stop in sortedBy { it.sequence }) {
        val current = grouped.lastOrNull()
        val continues = stop.placeId != null && current?.last()?.placeId == stop.placeId

        if (continues && current != null) {
            current.add(stop)
        } else {
            grouped.add(mutableListOf(stop))
        }
    }

    return grouped.map { stops -> StopGroup(stops.first().placeId, stops.toList()) }
}
