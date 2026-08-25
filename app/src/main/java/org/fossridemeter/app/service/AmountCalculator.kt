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

import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Ride

object AmountCalculator {
    fun calculate(
        ride: Ride,
        settings: Settings
    ): Ride {
        val distanceAmount =
            ride.meters * settings.perMeterRate

        // Time splits two ways. Seconds spent at a stop bill at
        // stoppedHourlyRate, everything else at hourlyRate - so a user
        // who waits at a door can charge for the wait at a different
        // price from the drive, which is what the two settings are for.
        //
        // Clamped because the two are counted by different clocks:
        // elapsedSeconds comes from TimeProvider, stoppedSeconds from the
        // stop rows and the dwell under way, and a rounding disagreement
        // must not be allowed to produce negative moving time.
        val stoppedSeconds = ride.stoppedSeconds.coerceIn(0, ride.elapsedSeconds)
        val movingSeconds = ride.elapsedSeconds - stoppedSeconds

        val timeAmount =
            movingSeconds / 3600.0 * settings.hourlyRate +
                    stoppedSeconds / 3600.0 * settings.stoppedHourlyRate

        val subtotal =
            settings.baseAmount + distanceAmount + timeAmount

        val totalAmount =
            maxOf(subtotal, settings.minimumAmount)

        return ride.copy(
            distanceAmount = distanceAmount,
            timeAmount = timeAmount,
            totalAmount = totalAmount
        )
    }
}