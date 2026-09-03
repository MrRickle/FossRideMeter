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
package org.fossridemeter.app.service

import org.fossridemeter.app.util.DistanceUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a GPS fix contributes to a ride's distance.
 *
 * Written after a real two-mile errand metered 1.9 miles for 4.4 miles
 * driven. The cause was that every rejected fix advanced the anchor, so
 * rejecting a fix also discarded the distance since the last accepted
 * one. These tests are mostly about that: what a *rejected* fix leaves
 * behind.
 */
class DistanceFilterTest {

    private val metres: (DistanceFilter.Fix, DistanceFilter.Fix) -> Double =
        { a, b -> DistanceUtil.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }

    /** Roughly 0.9 m of latitude per 0.000008 degrees. */
    private fun fix(
        north: Double = 0.0,
        accuracy: Float = 5f,
        speed: Float = 20f,
        hasSpeed: Boolean = true,
        atMillis: Long = 0L,
    ) = DistanceFilter.Fix(
        latitude = 37.7749 + north,
        longitude = -122.4194,
        accuracyMeters = accuracy,
        speedMps = speed,
        hasSpeed = hasSpeed,
        elapsedMillis = atMillis,
    )

    private fun judge(previous: DistanceFilter.Fix?, current: DistanceFilter.Fix, minimum: Double = 1.34112) =
        DistanceFilter.judge(previous, current, minimum, metres)

    @Test
    fun ordinaryMovementIsCounted() {
        val verdict = judge(fix(atMillis = 0), fix(north = 0.0009, atMillis = 5_000))

        assertTrue(verdict is DistanceFilter.Verdict.Count)
        assertEquals(100.0, (verdict as DistanceFilter.Verdict.Count).meters, 2.0)
    }

    /**
     * The heart of it. A vague fix must not take the journey with it -
     * it is skipped, the anchor stays, and the next usable fix measures
     * the whole distance from where the last usable one was.
     */
    @Test
    fun aVagueFixKeepsTheAnchorSoTheDistanceSurvivesIt() {
        val start = fix(atMillis = 0)

        assertEquals(
            DistanceFilter.Verdict.Skip,
            judge(start, fix(north = 0.0009, accuracy = 50f, atMillis = 5_000)),
        )

        // The anchor was kept, so the next good fix counts the lot.
        val recovered = judge(start, fix(north = 0.0018, atMillis = 10_000))

        assertTrue(recovered is DistanceFilter.Verdict.Count)
        assertEquals(200.0, (recovered as DistanceFilter.Verdict.Count).meters, 4.0)
    }

    /** The band the app displays as POOR is usable, and used to not be. */
    @Test
    fun poorAccuracyStillCounts() {
        val verdict = judge(fix(atMillis = 0), fix(north = 0.0009, accuracy = 25f, atMillis = 5_000))

        assertTrue(verdict is DistanceFilter.Verdict.Count)
    }

    /**
     * Slower than the minimum is not travelling, so nothing is added -
     * but the anchor stays, so pulling away from here is measured from
     * here. Advancing it was how every turn and traffic light quietly
     * deleted the ground either side of itself.
     */
    @Test
    fun belowTheMinimumSpeedAddsNothingAndKeepsTheAnchor() {
        val start = fix(atMillis = 0)

        assertEquals(
            DistanceFilter.Verdict.Skip,
            judge(start, fix(north = 0.0002, speed = 0.5f, atMillis = 5_000)),
        )

        val movingAgain = judge(start, fix(north = 0.0009, speed = 20f, atMillis = 10_000))

        assertTrue(movingAgain is DistanceFilter.Verdict.Count)
        assertEquals(100.0, (movingAgain as DistanceFilter.Verdict.Count).meters, 2.0)
    }

    /**
     * A provider that reports no speed reports 0. Gating on that would
     * meter nothing at all, forever, on any device that does it.
     */
    @Test
    fun aFixWithNoSpeedIsNotGatedOnSpeed() {
        val verdict = judge(
            fix(atMillis = 0),
            fix(north = 0.0009, speed = 0f, hasSpeed = false, atMillis = 5_000),
        )

        assertTrue(verdict is DistanceFilter.Verdict.Count)
    }

    /** A position that jumped rather than moved: re-anchor, bill nothing. */
    @Test
    fun animplausibleJumpIsNotBilled() {
        assertEquals(
            DistanceFilter.Verdict.Reanchor,
            judge(fix(atMillis = 0), fix(north = 0.05, atMillis = 1_000)),
        )
    }

    /**
     * A long gap is not a jump. Two minutes of tunnel at 25 m/s covers
     * three kilometres, and the old flat 100 m cap threw exactly this
     * away - which mattered much more once a skipped fix stopped moving
     * the anchor.
     */
    @Test
    fun aLongGapAtAPlausibleSpeedIsCounted() {
        val verdict = judge(fix(atMillis = 0), fix(north = 0.027, atMillis = 120_000))

        assertTrue(verdict is DistanceFilter.Verdict.Count)
        assertEquals(3000.0, (verdict as DistanceFilter.Verdict.Count).meters, 60.0)
    }

    @Test
    fun theFirstFixOfARideOnlySetsTheAnchor() {
        assertEquals(DistanceFilter.Verdict.Reanchor, judge(null, fix()))
    }
}
