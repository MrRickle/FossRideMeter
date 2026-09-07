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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Composing a place's name from the places that contain it.
 *
 * The shape is taken from Rick's own data, where the containment was
 * being encoded by hand into the names - "HD Lumber" inside "Home
 * Depot", "Aldi's Sparta" inside "Aldi's" - because nothing displayed
 * it.
 */
class PlaceNamesTest {

    /** Metres north of a fixed point, so containment is easy to reason about. */
    private fun place(
        name: String,
        northMetres: Double = 0.0,
        radius: Double,
        named: Boolean = true,
    ) = Place(
        id = name,
        name = name,
        latitude = 43.0 + northMetres / 111_320.0,
        longitude = -90.0,
        radiusMeters = radius,
        geohash = name,
        isNamed = named,
    )

    private val city = place("LaCrosse", radius = 5000.0)
    private val store = place("Home Depot", radius = 120.0)
    private val dept = place("Lumber", radius = 20.0)
    private val state = place("Washington", radius = 200_000.0)

    private val world = listOf(state, city, store, dept)

    @Test
    fun depthOfOneIsTheBareName() {
        assertEquals("Lumber", PlaceNames.composed(dept, world, depth = 1))
    }

    @Test
    fun depthOfTwoAddsTheSmallestContainingPlace() {
        assertEquals("Home Depot|Lumber", PlaceNames.composed(dept, world, depth = 2))
    }

    @Test
    fun depthWalksOutwardsSmallestFirst() {
        assertEquals(
            "LaCrosse|Home Depot|Lumber",
            PlaceNames.composed(dept, world, depth = 3),
        )
        assertEquals(
            "Washington|LaCrosse|Home Depot|Lumber",
            PlaceNames.composed(dept, world, depth = 4),
        )
    }

    /** Asking for more levels than exist shows what there is. */
    @Test
    fun depthBeyondTheChainIsNotPadded() {
        assertEquals(
            "Washington|LaCrosse|Home Depot|Lumber",
            PlaceNames.composed(dept, world, depth = 99),
        )
    }

    @Test
    fun aPlaceThatIsInsideNothingIsJustItself() {
        assertEquals("Washington", PlaceNames.composed(state, world, depth = 4))
    }

    /**
     * A geohash placeholder is bookkeeping the app invented. Naming a
     * stop after one would say nothing, so unnamed places never become
     * a parent - though they can still have one.
     */
    @Test
    fun anUnnamedPlaceIsNeverAParent() {
        val blob = place("9q8yyw3t", radius = 120.0, named = false)
        val inside = place("Counter", radius = 20.0)

        assertEquals("Counter", PlaceNames.composed(inside, listOf(blob, inside), depth = 3))
        assertEquals(
            "Home Depot|9q8yyw3t",
            PlaceNames.composed(blob, listOf(store.copy(radiusMeters = 300.0), blob), depth = 2),
        )
    }

    /** Same size cannot contain: otherwise two circles each claim the other. */
    @Test
    fun aPlaceTheSameSizeIsNotAParent() {
        val a = place("A", radius = 60.0)
        val b = place("B", radius = 60.0)

        assertEquals("A", PlaceNames.composed(a, listOf(a, b), depth = 3))
    }

    /** Far enough away not to contain it, however large. */
    @Test
    fun aLargePlaceElsewhereIsNotAParent() {
        val faraway = place("Madison", northMetres = 200_000.0, radius = 8000.0)

        assertEquals("Lumber", PlaceNames.composed(dept, listOf(faraway, dept), depth = 3))
    }
}
