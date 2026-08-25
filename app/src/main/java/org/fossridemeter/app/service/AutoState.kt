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

/**
 * What the automatic-by-location machinery is doing right now.
 *
 * Deliberately separate from RideStatus. A ride is READY, RUNNING or
 * PAUSED and that is a closed question about the ride itself (see the
 * comment in RideStatus and "Why there is no FINISHED state" in
 * architecture.md). Whether the app happens to be watching for a place
 * crossing is a different question that can be true or false alongside
 * any of those, so it is a second, orthogonal value rather than a fourth
 * status.
 */
sealed interface AutoState {

    /** Nothing to watch for - no place carries the relevant flag. */
    data object Off : AutoState

    /** Ride is READY; watching autoStart places to be left. */
    data object WatchingDeparture : AutoState

    /** Ride is RUNNING; watching autoSave places to be reached. */
    data object WatchingArrival : AutoState

    /**
     * Ride is PAUSED; watching autoStart places to be left, and leaving
     * one *resumes* the paused ride rather than starting a new one.
     *
     * A pause taken at a place is nearly always the middle of a ride -
     * lunch, a delivery, a wait - so driving off again means "carry on",
     * not "begin". Starting a second ride there would leave the first
     * paused forever and split one job in two.
     */
    data object WatchingResume : AutoState

    /**
     * Ride is PAUSED, no autoStart place exists to leave, but something
     * is still flagged - watching is stood down rather than switched off.
     * Nothing will happen until the user resumes by hand.
     *
     * Distinct from [Off], which means there is genuinely nothing to
     * watch for because no place carries a flag.
     */
    data object Suspended : AutoState

    /**
     * An arrival paused the ride and the grace window is counting down.
     * Resuming takes it back; running out commits the save.
     */
    data class PendingSave(
        val placeName: String,
        val secondsRemaining: Int,
    ) : AutoState
}
