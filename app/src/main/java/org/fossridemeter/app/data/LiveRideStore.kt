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
 *
 * It also holds the stop under way, if one has lasted long enough to be
 * worth keeping (see [SavedDwell]). A stop is only written to the
 * database when the vehicle drives off, so until then it exists only in
 * the meter's memory - and a long wait is exactly when the system is
 * most likely to reclaim the process. On 2026-10-10 two low-memory kills
 * during a stop threw away the hour and a half already spent there.
 */
class LiveRideStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markLive(rideId: String, status: RideStatus) {
        prefs.edit()
            .putString(KEY_RIDE_ID, rideId)
            .putString(KEY_STATUS, status.name)
            // A new ride starts with no stop under way, whatever an
            // earlier one left behind.
            .removeDwell()
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
        prefs.edit().remove(KEY_RIDE_ID).remove(KEY_STATUS).removeDwell().commit()
    }

    /**
     * A stop under way: where the vehicle has been sitting and since
     * when, the moment the ride was paused if it is, and the stop row
     * already written for it if the user pressed ADD STOP. Exactly the
     * dwell fields RideMeter would otherwise lose with the process.
     */
    data class SavedDwell(
        val latitude: Double,
        val longitude: Double,
        val startedAtMillis: Long,
        val pausedAtMillis: Long,
        val stopId: String?,
    )

    /**
     * Records the stop under way, or that there is none ([dwell] null).
     *
     * commit(), like the rest of this class, so it is on disk when the
     * kill comes. That makes it a synchronous write, which is why
     * RideMeter only calls this when the value changes, and only keeps a
     * stop that has lasted a while: while driving there is nothing to
     * keep and nothing is written.
     */
    fun saveDwell(dwell: SavedDwell?) {
        if (liveRideId() == null) return
        val editor = prefs.edit()
        if (dwell == null) {
            editor.removeDwell()
        } else {
            editor
                // Doubles as their bits: SharedPreferences has no double,
                // and a float would move the anchor by metres.
                .putLong(KEY_DWELL_LAT, dwell.latitude.toRawBits())
                .putLong(KEY_DWELL_LON, dwell.longitude.toRawBits())
                .putLong(KEY_DWELL_STARTED, dwell.startedAtMillis)
                .putLong(KEY_DWELL_PAUSED, dwell.pausedAtMillis)
                .putString(KEY_DWELL_STOP_ID, dwell.stopId)
        }
        editor.commit()
    }

    fun savedDwell(): SavedDwell? {
        if (!prefs.contains(KEY_DWELL_STARTED)) return null
        return SavedDwell(
            latitude = Double.fromBits(prefs.getLong(KEY_DWELL_LAT, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_DWELL_LON, 0L)),
            startedAtMillis = prefs.getLong(KEY_DWELL_STARTED, 0L),
            pausedAtMillis = prefs.getLong(KEY_DWELL_PAUSED, 0L),
            stopId = prefs.getString(KEY_DWELL_STOP_ID, null),
        )
    }

    private fun android.content.SharedPreferences.Editor.removeDwell() =
        remove(KEY_DWELL_LAT)
            .remove(KEY_DWELL_LON)
            .remove(KEY_DWELL_STARTED)
            .remove(KEY_DWELL_PAUSED)
            .remove(KEY_DWELL_STOP_ID)

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
        const val KEY_DWELL_LAT = "dwell_lat"
        const val KEY_DWELL_LON = "dwell_lon"
        const val KEY_DWELL_STARTED = "dwell_started"
        const val KEY_DWELL_PAUSED = "dwell_paused"
        const val KEY_DWELL_STOP_ID = "dwell_stop_id"
    }
}
