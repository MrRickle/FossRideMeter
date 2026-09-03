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

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Looper
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.util.DistanceUtil
import org.fossridemeter.app.model.Settings

class GpsDistanceProvider(
    context: Context,
    private val settings: Settings
) : DistanceProvider {

    companion object {
        private const val TAG = "RideMeter"
    }

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _distance =
        MutableStateFlow(0.0)

    override val distance =
        _distance.asStateFlow()

    private val _gpsInfo =
        MutableStateFlow(GpsInfo())

    override val gpsInfo =
        _gpsInfo.asStateFlow()

    private var previousLocation: Location? = null

    private var totalMeters = 0.0

    private val callback =
        object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                result.lastLocation?.let { location ->

                    Log.d(
                        "GPS",
                        "onLocationResult: accuracy=${location.accuracy}m"
                    )

                    Log.d ("GPS", "speed = ${location.speed} minimumSpeedMps = ${settings.minimumSpeedMps}")
                    val moving = location.speed >= settings.minimumSpeedMps
                    val accuracy = location.accuracy

                    _gpsInfo.value =
                        GpsInfo(
                            status =
                                when {

                                    accuracy <= 10f -> DistanceStatus.GOOD

                                    accuracy <= 30f -> DistanceStatus.POOR

                                    else -> DistanceStatus.WAITING
                                },
                            accuracy = accuracy,
                            rideLocation = RideLocation(
                                latitude = location.latitude,
                                longitude = location.longitude
                            ),
                            moving = moving
                        )

                    // DistanceFilter decides; this only carries out the
                    // verdict. The important half is Skip, which leaves
                    // previousLocation alone - a fix that cannot be
                    // trusted must not also consume the distance since
                    // the last one that could.
                    val verdict = DistanceFilter.judge(
                        previous = previousLocation?.asFix(),
                        current = location.asFix(),
                        minimumSpeedMps = settings.minimumSpeedMps,
                        distanceMeters = { a, b -> haversineMeters(a, b) },
                    )

                    when (verdict) {

                        is DistanceFilter.Verdict.Count -> {
                            totalMeters += verdict.meters
                            _distance.value = totalMeters
                            previousLocation = location

                            Log.d(
                                TAG,
                                "Moved %.2f m   Total %.2f m"
                                    .format(verdict.meters, totalMeters)
                            )
                        }

                        DistanceFilter.Verdict.Reanchor ->
                            previousLocation = location

                        DistanceFilter.Verdict.Skip ->
                            Log.d(
                                TAG,
                                "Skipped fix: accuracy=%.0fm speed=%.1f - anchor kept"
                                    .format(location.accuracy, location.speed)
                            )
                    }
                }
            }
        }

    @SuppressLint("MissingPermission")
    override fun start() {

        //    _status.value = DistanceStatus.WAITING
        Log.d("GPS", "Starting location updates")

        // Measure from the next fix, not from wherever the last one
        // was. start() is called on resume as well as at the beginning
        // of a ride, and the vehicle may have moved while paused - which
        // is time the user said not to bill. The total is kept; only the
        // anchor is dropped, so nothing is measured across a stretch
        // nobody was watching.
        //
        // This used to fall out of the flat 100 m cap discarding any big
        // jump, which rebaseForDeparture's comment relied on. Saying it
        // outright is better than depending on a filter meant for
        // something else - and the filter no longer does it, because a
        // long gap at a plausible speed is now real distance.
        previousLocation = null
        _gpsInfo.value = GpsInfo(
            status = DistanceStatus.WAITING,
            moving = false
        )
        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000
            )
                .setMinUpdateDistanceMeters(1f)
                .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }

    override fun stop() {

        fusedLocationClient.removeLocationUpdates(callback)
    }

    override fun reset() {

        previousLocation = null
        totalMeters = 0.0
        _distance.value = 0.0
    }
}

/** Only what DistanceFilter needs, so the decision stays testable. */
private fun Location.asFix() =
    DistanceFilter.Fix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMps = speed,
        hasSpeed = hasSpeed(),
        elapsedMillis = elapsedRealtimeNanos / 1_000_000L,
    )

/**
 * Great-circle metres between two fixes.
 *
 * Location.distanceTo() would do it, but taking primitives keeps the
 * filter testable off a device - and this is the same haversine the
 * place matching already uses, so a ride's distance and a place's
 * radius are measured the same way.
 */
private fun haversineMeters(a: DistanceFilter.Fix, b: DistanceFilter.Fix): Double =
    DistanceUtil.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
