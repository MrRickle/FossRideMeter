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

import org.fossridemeter.app.model.DistanceUnit
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.util.distanceToUnit
import org.fossridemeter.app.util.toDisplayRate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fare arithmetic, and in particular the split between time spent
 * driving and time spent at a stop.
 *
 * Pure input to output: no providers, no database, no Android. Which is
 * the argument for the arithmetic living in an object of its own.
 */
class AmountCalculatorTest {

    private val settings = Settings(
        perMeterRate = 0.001,       // $1 per km
        hourlyRate = 60.0,          // $1 per minute driving
        stoppedHourlyRate = 12.0,   // $0.20 per minute stopped
        baseAmount = 0.0,
        minimumAmount = 0.0,
    )

    private fun ride(meters: Double = 0.0, seconds: Long = 0, stopped: Long = 0) =
        Ride(
            id = "test",
            meters = meters,
            elapsedSeconds = seconds,
            stoppedSeconds = stopped,
        )

    @Test
    fun timeWithNoStopsBillsEntirelyAtTheHourlyRate() {
        val result = AmountCalculator.calculate(ride(seconds = 600), settings)
        assertEquals(10.0, result.timeAmount, 0.0001)
    }

    /** Ten minutes out, four of them stopped: 6 at $1, 4 at $0.20. */
    @Test
    fun stoppedSecondsBillAtTheStoppedRate() {
        val result = AmountCalculator.calculate(
            ride(seconds = 600, stopped = 240),
            settings
        )
        assertEquals(6.0 + 0.80, result.timeAmount, 0.0001)
    }

    /** The whole ride stopped - the hourly rate never applies. */
    @Test
    fun aRideThatNeverMovedBillsAllTimeAsStopped() {
        val result = AmountCalculator.calculate(
            ride(seconds = 600, stopped = 600),
            settings
        )
        assertEquals(2.0, result.timeAmount, 0.0001)
    }

    /**
     * Stopped time is counted from the stop rows, elapsed time by
     * TimeProvider. If the two ever disagree the moving half must not go
     * negative and start paying the user back.
     */
    @Test
    fun stoppedTimeBeyondElapsedIsClamped() {
        val result = AmountCalculator.calculate(
            ride(seconds = 600, stopped = 6000),
            settings
        )
        assertEquals(2.0, result.timeAmount, 0.0001)
    }

    /** Equal rates are the behaviour from before the stopped rate existed. */
    @Test
    fun equalRatesLeaveTheTotalUnchanged() {
        val flat = settings.copy(stoppedHourlyRate = settings.hourlyRate)
        val withStops = AmountCalculator.calculate(ride(seconds = 600, stopped = 240), flat)
        val without = AmountCalculator.calculate(ride(seconds = 600), flat)
        assertEquals(without.timeAmount, withStops.timeAmount, 0.0001)
    }

    @Test
    fun distanceAndBaseAreUnaffectedByStoppedTime() {
        val result = AmountCalculator.calculate(
            ride(meters = 5000.0, seconds = 600, stopped = 600),
            settings.copy(baseAmount = 3.0)
        )
        assertEquals(5.0, result.distanceAmount, 0.0001)
        assertEquals(3.0 + 5.0 + 2.0, result.totalAmount, 0.0001)
    }

    /**
     * The defaults a fresh install starts on, checked through the same
     * conversion the Settings screen uses.
     *
     * perMeterRate is stored per *meter* and shown per mile, and the two
     * drifted badly: 0.80 looked like eighty cents a mile and meant
     * $1,287.48 a mile on every new install. Nothing caught it, because
     * nothing anywhere asserted what the default looks like to the person
     * reading it.
     */
    @Test
    fun theDefaultRateIsSaneInTheUnitItIsDisplayedIn() {
        val shown = DistanceUnit.MILES.toDisplayRate(Settings().perMeterRate)
        assertEquals(1.75, shown, 0.005)
    }

    /**
     * The same guard for the minimum speed, which has the same shape of
     * trap: stored in meters per second, entered and shown in miles per
     * hour, and a number that looks reasonable in one is nonsense in the
     * other. The Settings screen converts with `distanceToUnit(…) * 3600`
     * and this checks the same arithmetic.
     */
    @Test
    fun theDefaultMinimumSpeedIsSaneInTheUnitItIsDisplayedIn() {
        val shown =
            distanceToUnit(Settings().minimumSpeedMps, DistanceUnit.MILES) * 3600

        assertEquals(3.0, shown, 0.001)
    }

    /**
     * The three that govern automatic behaviour. Nothing converts these,
     * so they can't go wrong the way a rate can — this is here because
     * they are a deliberate set, chosen against each other and against
     * how the app is actually driven, and a later edit to one of them
     * should be a decision rather than a typo. See "The Defaults a Fresh
     * Install Starts On" in docs/decisions.md.
     */
    @Test
    fun theAutomaticBehaviourDefaultsAreTheOnesThatWereChosen() {
        val defaults = Settings()

        assertEquals(3, defaults.stopDetectionMinutes)
        assertEquals(30, defaults.autoWatchSeconds)
        assertEquals(2, defaults.autoSaveGraceMinutes)
    }

    /** A ten-mile, half-hour ride on the defaults, as a sanity check. */
    @Test
    fun aTypicalRideOnTheDefaultsCostsATypicalAmount() {
        val defaults = Settings()
        val result = AmountCalculator.calculate(
            ride(meters = 16093.44, seconds = 1800),
            defaults
        )
        assertEquals(17.50 + 7.50, result.totalAmount, 0.05)
    }

    @Test
    fun theMinimumStillWins() {
        val result = AmountCalculator.calculate(
            ride(seconds = 60, stopped = 60),
            settings.copy(minimumAmount = 25.0)
        )
        assertEquals(25.0, result.totalAmount, 0.0001)
    }
}
