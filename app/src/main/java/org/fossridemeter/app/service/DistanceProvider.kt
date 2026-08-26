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

import kotlinx.coroutines.flow.Flow

/**
 * A source of distance travelled, in meters, plus the fixes behind it.
 *
 * The lifecycle is the part worth stating, because RideMeter depends on
 * it and an implementation that gets it wrong is not obviously wrong on
 * its own:
 *
 * * [start] **resumes**. It is called on every resume as well as at the
 *   beginning of a ride, so it must never clear the total: a paused ride
 *   picks up its odometer where it left it. RideMeter.start() builds a
 *   fresh provider for each new ride, so there is nothing for [start] to
 *   clean up anyway.
 * * [stop] suspends collection and keeps the total.
 * * [reset] is the only thing that zeroes. RideMeter calls it when a
 *   ride ends and when a departure is rebased.
 *
 * SimulatedDistanceProvider.start() used to reset, which sent a paused
 * simulated ride's distance back to 0 on resume and replayed its
 * scripted timeline from the top.
 */
interface DistanceProvider {

    val distance: Flow<Double>

    val gpsInfo: Flow<GpsInfo>

    /** Begins or resumes producing fixes. Never clears the total. */
    fun start()

    /** Stops producing fixes. The total is kept. */
    fun stop()

    /** Stops, and returns the total and any internal position to zero. */
    fun reset()
}