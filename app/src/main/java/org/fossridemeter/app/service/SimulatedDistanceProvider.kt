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
 * The script is a round trip - out along a fixed heading, three halts,
 * then back down the same line to where it began - and it is chosen to
 * exercise the app's own thresholds rather than just to move a number.
 * See [phases] for what each leg is for. It runs about 19 minutes and
 * covers a little over 5 miles.
 *
 * One thing it cannot test: `Settings.minimumSpeedMps`. That gate lives
 * in [GpsDistanceProvider], which decides from a fix's reported speed
 * whether to count the movement. This provider reports distance
 * directly, so no speed here is ever gated and a leg slower than the
 * minimum would still bill. Test that one on real GPS.
 */
class SimulatedDistanceProvider : DistanceProvider {

    private companion object {
        const val TAG = "RideMeter"

        // Default hardcoded start location for the simulated ride.
        const val START_LATITUDE = 37.7749
        const val START_LONGITUDE = -122.4194

        // Compass heading (degrees) the outbound legs travel along, and
        // the reciprocal the return legs come back on. Mirroring the
        // outbound legs against it is what lands the ride back home
        // rather than merely near it.
        const val HEADING_DEGREES = 45.0
        const val RETURN_HEADING_DEGREES = HEADING_DEGREES + 180.0

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
        val status: DistanceStatus,
        val headingDegrees: Double = HEADING_DEGREES
    ) {
        val moving: Boolean get() = speedMph > 0.0
    }

    /**
     * The legs of the simulated ride, in order.
     *
     * The durations are picked against the defaults in [Settings] so a
     * single run says whether they behave:
     *
     * * The three halts are **30 s, 2 min and 3.5 min**, which straddles
     *   the 3-minute `stopDetectionMinutes` default from both sides. The
     *   first two must stay traffic and produce no `Stop`; the third must
     *   become one and bill at `stoppedHourlyRate`.
     * * The four minutes parked at home are over the threshold too, and
     *   the 40 m reposition after them is what turns that dwell into a
     *   recorded `Stop` - a dwell is only written down when the vehicle
     *   departs it. Because the reposition comes straight back, that
     *   stop sits at the place the ride ends, so **`save()` must retract
     *   it**. A saved run therefore holds one stop and a `Stopped` time
     *   of 3:30, the middle halt alone; a run inspected while still
     *   paused holds two. Either count being wrong is a bug, and so is a
     *   `Stopped` figure that still carries the four minutes at home.
     * * The **first driving leg is two minutes**, comfortably longer than
     *   a departure needs to be confirmed - 60 s and two fixes, polled
     *   every `autoWatchSeconds` (30 s) - so a place flagged `autoStart`
     *   fires during that leg rather than after the script has moved on.
     *   It clears a placeholder's 61 m radius, and the 100 m beyond it
     *   that counts as a decisive departure, within fifteen seconds.
     * * The return runs at 80 mph and then 25 mph for the last stretch,
     *   and cancels the outbound legs **exactly**: distance is
     *   speed x duration, and 80x105 + 25x30 is 9,150 mph-seconds, the
     *   same as the outbound 35x120 + 25x90 + 45x60. So the ride ends on
     *   the coordinates it started from rather than near them - verified
     *   on a real run, which named itself "Home -> Home". Any change to
     *   a driving leg has to keep those two sums equal.
     * * Those same four minutes give an `autoSave` arrival time to be
     *   noticed and `autoSaveGraceMinutes` (2) time to run out.
     *
     * * The two parks at home either side of a 40 m shuffle are **one
     *   visit**, and must produce **one** stop between them, not two.
     *   The vehicle never leaves home, so the second park extends the
     *   first park's stop. A run that ends up with two rows at home has
     *   found a bug.
     *
     * It totals 8,180 m - 5.08 miles - and the return legs cancel the
     * outbound ones exactly, so the finishing coordinates are the
     * starting ones rather than merely close to them.
     */
    private val phases = listOf(

        // Acquiring a fix. Ten seconds rather than the minute this used
        // to be: long enough to see WAITING on screen, short enough that
        // nobody testing has to sit through it.
        Phase(durationSeconds = 10, speedMph = 0.0, status = DistanceStatus.WAITING),

        // Out. Long enough for an auto-start to confirm and fire.
        Phase(durationSeconds = 120, speedMph = 35.0, status = DistanceStatus.GOOD),

        // A light. Under the threshold, so not a stop.
        Phase(durationSeconds = 30, speedMph = 0.0, status = DistanceStatus.GOOD),

        // POOR for a stretch, so the GPS status on screen is seen to move.
        Phase(durationSeconds = 90, speedMph = 25.0, status = DistanceStatus.POOR),

        // Still under the threshold at two minutes, so still not a stop.
        Phase(durationSeconds = 120, speedMph = 0.0, status = DistanceStatus.GOOD),

        Phase(durationSeconds = 60, speedMph = 45.0, status = DistanceStatus.GOOD),

        // Over the threshold. This one is a stop, and bills as one.
        Phase(durationSeconds = 210, speedMph = 0.0, status = DistanceStatus.GOOD),

        // Home, back down the same line: highway, then a slower approach.
        // The two together cover the outbound distance exactly - see the
        // note on cancelling out, below.
        Phase(durationSeconds = 105, speedMph = 80.0, status = DistanceStatus.GOOD, headingDegrees = RETURN_HEADING_DEGREES),
        Phase(durationSeconds = 30, speedMph = 25.0, status = DistanceStatus.GOOD, headingDegrees = RETURN_HEADING_DEGREES),

        // Parked at home, past stopDetectionMinutes, and long enough for
        // an arrival to be noticed and the automatic save's grace window
        // to elapse.
        Phase(durationSeconds = 240, speedMph = 0.0, status = DistanceStatus.GOOD),

        // Reposition: 40 m out, park again, 40 m back.
        //
        // Two things at once, both about what happens inside a place
        // rather than between places.
        //
        // The moves are departures: a dwell is only written down when
        // the vehicle leaves its anchor, and 40 m clears the 30 m dwell
        // anchor radius while staying well inside home. So the first
        // move ends the park above and writes the stop, and the second
        // ends the park between them.
        //
        // Those two parks are one visit - the vehicle never left home -
        // so the second must *extend* the first's stop rather than add
        // another. One row spanning both parks and the shuffle between
        // them is the whole point: before that, a visit anywhere large
        // enough to move around in became a stop per move.
        //
        // Coming back to the start also leaves the ride ending where it
        // began, which makes that stop's place the end place, which is
        // what save()'s trailing-stop retraction is for.
        Phase(durationSeconds = 9, speedMph = 10.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 240, speedMph = 0.0, status = DistanceStatus.GOOD),
        Phase(durationSeconds = 9, speedMph = 10.0, status = DistanceStatus.GOOD, headingDegrees = RETURN_HEADING_DEGREES),

        // Settle. Under the threshold, so it adds no stop of its own.
        Phase(durationSeconds = 90, speedMph = 0.0, status = DistanceStatus.GOOD),
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

    // Where the timeline had got to. Held across a stop so start() can
    // pick the script up rather than replay it - see start().
    private var phaseIndex = 0
    private var tickInPhase = 0

    private var latitude = START_LATITUDE
    private var longitude = START_LONGITUDE

    /**
     * Runs the scripted timeline, continuing from wherever it stopped.
     *
     * start() must *resume*, not restart: RideMeter.resume() calls it
     * after a pause, on the documented understanding that pausing never
     * reset the providers. This used to call resetSimulationState()
     * here, which zeroed the odometer and replayed the script from the
     * top - so pausing a simulated ride and resuming it sent the
     * distance back to 0, put the vehicle back at the start
     * coordinates, and made the user sit through the opening minute of
     * WAITING again.
     *
     * GpsDistanceProvider.start() has always behaved this way - it only
     * re-registers for updates and keeps its total - and reset() is what
     * zeroes either of them. A new ride gets a new provider from
     * RideMeter.start() regardless, so nothing depends on start()
     * clearing anything.
     */
    override fun start() {

        job?.cancel()

        job = scope.launch {

            Log.d(TAG, "Simulator started at phase $phaseIndex tick $tickInPhase")

            // Report where the vehicle actually is: the first phase on a
            // fresh start, whatever the script had reached on a resume.
            phases.getOrNull(phaseIndex)?.let { phase ->
                _gpsInfo.value = GpsInfo(
                    status = phase.status,
                    accuracy = if (phase.status == DistanceStatus.WAITING) 0f else 5f,
                    rideLocation = currentLocation(),
                    moving = false
                )
            }

            while (phaseIndex < phases.size) {

                val phase = phases[phaseIndex]

                while (tickInPhase < phase.durationSeconds) {

                    if (!isActive) return@launch

                    delay(TICK)

                    advance(phase)
                    tickInPhase++

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

                phaseIndex++
                tickInPhase = 0
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
        phaseIndex = 0
        tickInPhase = 0
        latitude = START_LATITUDE
        longitude = START_LONGITUDE
        _distance.value = 0.0
        _gpsInfo.value = GpsInfo()
    }

    /** Advances the simulated position/distance by one tick of [phase]. */
    private fun advance(phase: Phase) {

        val metersThisTick = phase.speedMph * MPH_TO_MPS * TICK.inWholeSeconds

        if (phase.moving && metersThisTick > 0.0) {

            val headingRadians = Math.toRadians(phase.headingDegrees)
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
