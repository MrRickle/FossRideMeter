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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copying places from one phone to another: named places only, nothing
 * duplicated, and a radius already set here is never changed without
 * asking.
 */
class PlacesImportPlanTest {

    /** Roughly 1.1 m of latitude per 0.00001 degrees. */
    private fun place(
        id: String,
        north: Double = 0.0,
        radius: Double = 50.0,
        named: Boolean = true,
    ) = Place(
        id = id,
        name = id,
        latitude = 37.7749 + north,
        longitude = -122.4194,
        radiusMeters = radius,
        geohash = "",
        isNamed = named,
    )

    @Test
    fun unnamedPlacesInTheFileAreSkipped() {
        val plan = planPlacesImport(listOf(place("a", named = false)), emptyList())

        assertTrue(plan.toWrite.isEmpty())
        assertEquals(1, plan.skippedUnnamed)
    }

    @Test
    fun aNewNamedPlaceIsWritten() {
        val plan = planPlacesImport(listOf(place("a")), listOf(place("b", north = 0.01)))

        assertEquals(listOf("a"), plan.toWrite.map { it.id })
    }

    @Test
    fun theSamePlaceAveragedDifferentlyIsAlreadyHere() {
        // Different id, a few metres off - each phone averaged its own rides.
        val plan = planPlacesImport(listOf(place("file", north = 0.00005)), listOf(place("here")))

        assertTrue(plan.toWrite.isEmpty())
        assertEquals(1, plan.alreadyHere)
    }

    @Test
    fun aDifferentRadiusAtTheSameSpotIsAConflictNotAWrite() {
        val here = place("here", radius = 50.0)
        val file = place("file", north = 0.00005, radius = 80.0)

        val plan = planPlacesImport(listOf(file), listOf(here))

        assertTrue(plan.toWrite.isEmpty())
        assertEquals(listOf(RadiusConflict(existing = here, incoming = file)), plan.conflicts)
    }

    @Test
    fun aRadiusThatOnlyDiffersByRoundingIsTheSame() {
        val plan = planPlacesImport(listOf(place("a", radius = 15.24)), listOf(place("a", radius = 15.0)))

        assertTrue(plan.conflicts.isEmpty())
        assertEquals(1, plan.alreadyHere)
    }

    @Test
    fun anUnnamedPlaceHereIsNotAMatch() {
        val plan = planPlacesImport(listOf(place("file", radius = 80.0)), listOf(place("here", named = false)))

        assertEquals(listOf("file"), plan.toWrite.map { it.id })
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun aPlaceInsideAnotherIsItsOwnPlace() {
        // A shop 30 m inside a 200 m mall is not the mall.
        val plan = planPlacesImport(
            listOf(place("shop", north = 0.00027, radius = 20.0)),
            listOf(place("mall", radius = 200.0)),
        )

        assertEquals(listOf("shop"), plan.toWrite.map { it.id })
    }

    @Test
    fun theNearestMatchWins() {
        val near = place("near", north = 0.00002, radius = 30.0)
        val far = place("far", north = -0.00008, radius = 40.0)

        val plan = planPlacesImport(listOf(place("file", radius = 90.0)), listOf(far, near))

        assertEquals(near, plan.conflicts.single().existing)
    }
}
