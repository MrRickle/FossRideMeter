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

import android.content.Context

/**
 * The id of the ride currently being metered, kept outside the process
 * that is metering it.
 *
 * A ride's row is written from the moment it starts, but nothing in it
 * says the ride is still running: `RideRecord` has no status, and
 * `endTime == startTime` - what a live row looks like, since
 * `Ride.toRecord` falls back to the start time - is a guess, not a fact.
 * This is the fact. Set when a ride starts, cleared when it is saved or
 * cancelled, so anything still here at startup is a ride that was
 * interrupted rather than finished.
 *
 * SharedPreferences rather than the settings DataStore: this is not a
 * setting, nobody edits it, and it has to be readable at service create
 * before anything else is ready. Writes use commit() - the whole value of
 * the marker is that it is already on disk when the process is killed
 * without warning, which is precisely when apply() has no obligation to
 * have finished.
 */
class LiveRideStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markLive(rideId: String) {
        prefs.edit().putString(KEY_RIDE_ID, rideId).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_RIDE_ID).commit()
    }

    fun liveRideId(): String? = prefs.getString(KEY_RIDE_ID, null)

    private companion object {
        const val PREFS = "live_ride"
        const val KEY_RIDE_ID = "ride_id"
    }
}
