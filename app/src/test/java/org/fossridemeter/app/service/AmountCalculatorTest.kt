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

import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.Settings
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

    @Test
    fun theMinimumStillWins() {
        val result = AmountCalculator.calculate(
            ride(seconds = 60, stopped = 60),
            settings.copy(minimumAmount = 25.0)
        )
        assertEquals(25.0, result.totalAmount, 0.0001)
    }
}
