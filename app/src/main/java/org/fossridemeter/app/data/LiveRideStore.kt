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
import org.fossridemeter.app.model.RideStatus

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
 * It carries the ride's *status* too, so the ride can come back doing
 * what it was doing rather than always coming back paused. The row
 * cannot answer that - RideRecord has no status - and by the time
 * anyone asks, the process that knew is gone.
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

    fun markLive(rideId: String, status: RideStatus) {
        prefs.edit()
            .putString(KEY_RIDE_ID, rideId)
            .putString(KEY_STATUS, status.name)
            .commit()
    }

    /**
     * Records that the live ride changed state, so a process killed
     * later comes back doing what it was doing.
     *
     * Written on pause and resume only - both rare - rather than on the
     * persist tick, because commit() is a synchronous disk write and the
     * status is the only part that changes without the row changing.
     */
    fun markStatus(status: RideStatus) {
        if (liveRideId() == null) return
        prefs.edit().putString(KEY_STATUS, status.name).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_RIDE_ID).remove(KEY_STATUS).commit()
    }

    fun liveRideId(): String? = prefs.getString(KEY_RIDE_ID, null)

    /**
     * What the interrupted ride was doing when the process died, or null
     * if it was marked by a build that didn't record one. PAUSED is the
     * safe reading of null - it is what every interrupted ride used to
     * come back as.
     */
    fun liveRideStatus(): RideStatus? =
        prefs.getString(KEY_STATUS, null)?.let { name ->
            RideStatus.entries.firstOrNull { it.name == name }
        }

    private companion object {
        const val PREFS = "live_ride"
        const val KEY_RIDE_ID = "ride_id"
        const val KEY_STATUS = "status"
    }
}
