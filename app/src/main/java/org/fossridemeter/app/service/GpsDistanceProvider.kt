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

                    if (!moving) {
                        previousLocation = location
                        return@let
                    }

                    previousLocation?.let { previous ->
                        Log.d(
                            "GPS",
                            "Location ${location.latitude}, ${location.longitude}"
                        )
                        val meters =
                            previous.distanceTo(location)

                        if (meters > 100) {
                            previousLocation = location
                            return@let
                        }

                        // Ignore GPS drift
                        if (
                            location.accuracy in 0f..<10f &&
                            location.speed in 0f..<45f
                        ) {
                            totalMeters += meters
                            _distance.value = totalMeters

                            Log.d(
                                TAG,
                                "Moved %.2f m   Total %.2f m"
                                    .format(meters, totalMeters)

                            )
                        }
                    }
                    previousLocation = location
                }
            }
        }

    @SuppressLint("MissingPermission")
    override fun start() {

        //    _status.value = DistanceStatus.WAITING
        Log.d("GPS", "Starting location updates")
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