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

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fossridemeter.app.model.RideLocation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.time.Duration.Companion.seconds

/**
 * Simulates a ride so the UI can be exercised without real GPS hardware.
 *
 * Timeline:
 *  1. Sit at the start location for 60s while a fix is "acquired" (WAITING).
 *  2. Move at ~20 mph for 10s.
 *  3. Move at ~60 mph for 50s.
 *  4. Stop for 60s.
 *  5. Move for 60s.
 *  6. Stop for 300s (5 min).
 *  7. Move for 60s.
 *  8. Come to a final stop at the resulting location.
 */
class SimulatedDistanceProvider : DistanceProvider {

    private companion object {
        const val TAG = "RideMeter"

        // Default hardcoded start location for the simulated ride.
        const val START_LATITUDE = 37.7749
        const val START_LONGITUDE = -122.4194

        // Fixed compass heading (degrees) the simulated ride travels along.
        const val HEADING_DEGREES = 45.0

        const val METERS_PER_DEGREE_LATITUDE = 111_320.0

        const val MPH_TO_MPS = 0.44704

        val TICK = 1.seconds
    }

    /**
     * One leg of the simulated ride.
     *
     * @param durationSeconds how long this leg lasts
     * @param speedMph speed to travel at during this leg (0 = stopped)
     * @param status the [DistanceStatus] to report while this leg runs
     */
    private data class Phase(
        val durationSeconds: Int,
        val speedMph: Double,
        val status: DistanceStatus
    ) {
        val moving: Boolean get() = speedMph > 0.0
    }

    private val phases = listOf(
        Phase(durationSeconds = 60, speedMph = 0.0, status = DistanceStatus.WAITING),
        Phase(durationSeconds = 10, speedMph = 20.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 50, speedMph = 60.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 60, speedMph = 0.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 60, speedMph = 20.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 300, speedMph = 0.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 60, speedMph = 20.0, status = DistanceStatus.GOOD),
    )

    private val _distance =
        MutableStateFlow(0.0)

    override val distance: Flow<Double> =
        _distance.asStateFlow()

    private val _gpsInfo =
        MutableStateFlow(GpsInfo())

    override val gpsInfo =
        _gpsInfo.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private var job: Job? = null

    private var totalMeters = 0.0
    private var latitude = START_LATITUDE
    private var longitude = START_LONGITUDE

    override fun start() {

        job?.cancel()
        resetSimulationState()

        job = scope.launch {

            Log.d(TAG, "Simulator started")

            _gpsInfo.value = GpsInfo(
                status = DistanceStatus.WAITING,
                accuracy = 0f,
                rideLocation = currentLocation(),
                moving = false
            )

            for (phase in phases) {

                if (!isActive) break

                repeat(phase.durationSeconds) {

                    if (!isActive) return@repeat

                    delay(TICK)

                    advance(phase)

                    Log.d(
                        TAG,
                        "phase=${phase.status} speed=${phase.speedMph}mph " +
                            "total=%.2fm lat=%.6f lon=%.6f".format(
                                totalMeters,
                                latitude,
                                longitude
                            )
                    )
                }
            }

            // Final stop: parked at the last computed location.
            _gpsInfo.value = GpsInfo(
                status = DistanceStatus.GOOD,
                accuracy = 5f,
                rideLocation = currentLocation(),
                moving = false
            )

            Log.d(TAG, "Simulator finished, total=%.2fm".format(totalMeters))
        }
    }

    override fun stop() {

        job?.cancel()
    }

    override fun reset() {

        stop()
        resetSimulationState()
    }

    private fun resetSimulationState() {

        totalMeters = 0.0
        latitude = START_LATITUDE
        longitude = START_LONGITUDE
        _distance.value = 0.0
        _gpsInfo.value = GpsInfo()
    }

    /** Advances the simulated position/distance by one tick of [phase]. */
    private fun advance(phase: Phase) {

        val metersThisTick = phase.speedMph * MPH_TO_MPS * TICK.inWholeSeconds

        if (phase.moving && metersThisTick > 0.0) {

            val headingRadians = Math.toRadians(HEADING_DEGREES)
            val metersPerDegreeLongitude =
                METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))

            latitude += (metersThisTick * cos(headingRadians)) / METERS_PER_DEGREE_LATITUDE
            longitude += (metersThisTick * sin(headingRadians)) / metersPerDegreeLongitude

            totalMeters += metersThisTick
            _distance.value = totalMeters
        }

        _gpsInfo.value = GpsInfo(
            status = phase.status,
            accuracy = if (phase.status == DistanceStatus.WAITING) 0f else 5f,
            rideLocation = currentLocation(),
            moving = phase.moving
        )
    }

    private fun currentLocation() =
        RideLocation(
            latitude = latitude,
            longitude = longitude
        )
}
