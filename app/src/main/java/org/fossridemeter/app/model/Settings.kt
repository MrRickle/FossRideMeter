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
    val minimumSpeedMps: Double = 0.0,
    // GPS, because a fresh install's first ride has to be a real one.
    // The simulator is still there, one tap away in Settings - it just
    // isn't what someone who installed a ride meter asked for.
    val distanceProvider: DistanceProviderType = DistanceProviderType.GPS,
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val measurementSystem: MeasurementSystem = MeasurementSystem.US,
    val baseAmount: Double = 0.0,
    val minimumAmount: Double = 0.0,
    // How long, stationary, before a dwell period counts as a stop rather
    // than just sitting at a light or in traffic.
    val stopDetectionMinutes: Int = 5,

    // How often PlaceWatcher takes a coarse fix while watching for a
    // departure. This is the whole battery budget of auto-start: the app
    // is otherwise idle, so the poll interval is the cost. Arrivals cost
    // nothing extra - they're read off the fixes the running ride is
    // already collecting.
    val autoWatchSeconds: Int = 60,

    // How each of those checks gets its fix. Defaults to GPS: the
    // two-tier alternative saves real battery while parked but routinely
    // cannot resolve a place from a coarse fix, and the escalations that
    // recovers from cost more delay than the saving is worth by default.
    val autoWatchAccuracy: WatchAccuracy = WatchAccuracy.GPS,

    // After an automatic arrival pauses a ride, how long to wait before
    // committing it. The window exists so an arrival that turns out to be
    // a stop along the way can be undone by resuming, rather than being
    // saved out from under the user.
    val autoSaveGraceMinutes: Int = 5,

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
