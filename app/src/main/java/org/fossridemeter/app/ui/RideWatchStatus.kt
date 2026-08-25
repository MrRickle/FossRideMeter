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
package org.fossridemeter.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.fossridemeter.app.service.AutoState
import org.fossridemeter.app.service.PlaceWatcher

/**
 * What the automatic-by-location watcher is doing, and where it thinks
 * the vehicle is.
 *
 * This exists because the meter's own GPS line reports the *ride's*
 * DistanceProvider, which doesn't exist until a ride starts - so it reads
 * "GPS Off" for the entire time watching is the only thing running, and
 * looks for all the world like nothing is happening. Nothing on this
 * screen distinguished armed from idle, which made a failed auto-start
 * impossible to reason about from the device.
 *
 * It sits in the info section rather than in the column with the meter,
 * because that column does not scroll and the buttons are in it: in
 * landscape it is a fixed 330 dp, and three more lines above the buttons
 * pushed them off the bottom of the screen. The info section scrolls, so
 * it can absorb them. Where there is no room for an info section at all,
 * RideLive draws it in the column instead - cramped beats invisible, and
 * this is the one readout that says whether watching is alive.
 *
 * The fix line is deliberately raw - coordinates, accuracy, geohash, and
 * which place (if any) currently contains it. That is precisely the input
 * to the containment decision, so a place that never matches can be
 * spotted while standing in it.
 */
@Composable
fun RideWatchStatus(
    autoState: AutoState,
    watchFix: PlaceWatcher.WatchFix?,
) {

    if (autoState == AutoState.Off && watchFix == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = when (autoState) {
                AutoState.Off -> "Auto off - no place is flagged"
                // Named where it can be: "will resume on leaving Home" is
                // the whole answer, and the place is only knowable from
                // the fix - the state itself doesn't carry one.
                AutoState.WatchingResume ->
                    watchFix?.insidePlaceName
                        ?.let { "Paused - will resume on leaving $it" }
                        ?: "Paused - will resume on leaving an auto-start place"
                AutoState.Suspended -> "Auto paused - resume to re-arm"
                AutoState.WatchingDeparture -> "Watching for departure"
                // An arrival can't fire while the vehicle is still sitting
                // in the place it would arrive at: the watcher seeded
                // itself inside, so nothing crosses until it leaves and
                // comes back. Saying only "watching for arrival" while
                // parked at home reads as though a save were imminent.
                AutoState.WatchingArrival ->
                    watchFix?.insidePlaceName
                        ?.let { "Running - not left $it yet" }
                        ?: "Watching for arrival"
                is AutoState.PendingSave ->
                    "Arrived at ${autoState.placeName} - saving in " +
                        "%d:%02d".format(
                            autoState.secondsRemaining / 60,
                            autoState.secondsRemaining % 60,
                        )
            },
            style = MaterialTheme.typography.labelLarge
        )

        watchFix?.let { fix ->
            // The provider is named because it explains the accuracy:
            // sweeps use the cheapest one going, a running ride uses GPS
            // at 1 Hz, and the difference between them is hundreds of
            // metres rather than a fault.
            val accuracyText =
                if (fix.accuracyMeters > 0f) "±%.0fm".format(fix.accuracyMeters) else "±?"

            Text(
                text = "%.5f, %.5f  %s  %s  %s".format(
                    fix.latitude,
                    fix.longitude,
                    accuracyText,
                    fix.provider,
                    fix.geohash,
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = fix.insidePlaceName?.let { "inside $it" } ?: "not inside any watched place",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
