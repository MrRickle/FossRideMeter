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
package org.fossridemeter.app.model


data class Settings(
    // $1.75 per mile, which is what this has to be written as once it is
    // per *meter*: 1.75 / 1609.344. Rates are stored in SI and converted
    // only for display, so the number here is never the number the user
    // sees - and that is exactly how it went wrong. It read 0.80, looked
    // like eighty cents a mile, and meant $1,287.48 a mile on every fresh
    // install. AmountCalculatorTest pins the displayed figure so the two
    // can't drift apart again.
    val perMeterRate: Double = 0.0010874,
    val hourlyRate: Double = 15.00,
    val stoppedHourlyRate: Double = 15.00,
    // 3 mph, in meters per second, entered and shown in the unit the
    // user picked. A fix slower than this contributes no distance at
    // all (GpsDistanceProvider), so the threshold has to sit below the
    // slowest speed anyone actually drives at: 0.0 - the old default -
    // let a parked phone's drift meter as distance, but anything up
    // near traffic speed would silently drop real miles crawled in a
    // car park or a jam. Walking pace splits the two.
    val minimumSpeedMps: Double = 1.34112,
    // GPS, because a fresh install's first ride has to be a real one.
    // The simulator is still there, one tap away in Settings - it just
    // isn't what someone who installed a ride meter asked for.
    val distanceProvider: DistanceProviderType = DistanceProviderType.GPS,
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val measurementSystem: MeasurementSystem = MeasurementSystem.US,
    val baseAmount: Double = 0.0,
    val minimumAmount: Double = 0.0,
    // How long, stationary, before a dwell period counts as a stop rather
    // than just sitting at a light or in traffic. Three minutes: long
    // enough that no ordinary light or queue reaches it, short enough
    // that a real wait at a door is charged at the stopped rate rather
    // than the driving one.
    val stopDetectionMinutes: Int = 3,

    // How often PlaceWatcher takes a coarse fix while watching for a
    // departure. This is the whole battery budget of auto-start: the app
    // is otherwise idle, so the poll interval is the cost. Arrivals cost
    // nothing extra - they're read off the fixes the running ride is
    // already collecting. It is the *idle* interval only: the first fix
    // outside a place makes the crossing a candidate, and from there
    // PlaceWatcher polls at CANDIDATE_POLL_SECONDS to confirm. So this
    // number buys how quickly a departure is first suspected, and 30 s
    // rather than 60 s halves the wait for it at the cost of twice the
    // idle fixes.
    val autoWatchSeconds: Int = 30,

    // How each of those checks gets its fix. Defaults to GPS: the
    // two-tier alternative saves real battery while parked but routinely
    // cannot resolve a place from a coarse fix, and the escalations that
    // recovers from cost more delay than the saving is worth by default.
    val autoWatchAccuracy: WatchAccuracy = WatchAccuracy.GPS,

    // After an automatic arrival pauses a ride, how long to wait before
    // committing it. The window exists so an arrival that turns out to be
    // a stop along the way can be undone by resuming, rather than being
    // saved out from under the user. Two minutes: the window is only
    // useful for as long as the user is still near the vehicle to notice
    // the notification, and a ride that has genuinely ended shouldn't sit
    // uncommitted for five.
    val autoSaveGraceMinutes: Int = 2,

    // A tone on start, pause, resume, save, and cancel. On by default:
    // the actions that most need announcing are the automatic ones, and
    // those happen with the phone in a pocket. Plays on the notification
    // stream, so silencing the phone silences these too.
    val soundEnabled: Boolean = true,

    // What each action sounds like. Null means the built-in tone, which
    // is what a fresh install gets: it needs no assets and the five are
    // already distinct from each other. A non-null value is a
    // content:// Uri picked from the system sound picker - the same list
    // Android's own sound settings offer - and is stored as a string
    // because that is what a Uri survives as.
    val startSoundUri: String? = null,
    val pauseSoundUri: String? = null,
    val resumeSoundUri: String? = null,
    val saveSoundUri: String? = null,
    val cancelSoundUri: String? = null,
)
