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
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.fossridemeter.app.data.PlaceDao
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.model.WatchAccuracy
import org.fossridemeter.app.util.DistanceUtil
import org.fossridemeter.app.util.EventLog
import org.fossridemeter.app.util.GeohashUtil
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Watches for the vehicle crossing the boundary of a place the user has
 * flagged, and reports the crossing. It decides nothing about rides - it
 * only answers "we just left X" / "we just arrived at X" and hands that
 * to RideTrackingService.
 *
 * Deliberately built on android.location.LocationManager rather than
 * Play Services. GpsDistanceProvider is the app's only Google dependency
 * and there's no reason to add a second one: a fix a minute needs
 * nothing fused, and geofencing - the obvious Play Services answer here -
 * would make the dependency permanent and unremovable.
 *
 * ## Which places
 *
 * [Mode] picks the flag and the crossing that matters:
 *
 * * [Mode.DEPARTURE] - places with autoStart, reported when left.
 * * [Mode.ARRIVAL] - places with autoSave, reported when reached.
 *
 * Containment is the same single rule PlaceResolver uses - distance <=
 * place.radiusMeters - so a place means exactly what it means everywhere
 * else in the app. Nothing here creates a Place; only flagged, already
 * existing ones are ever considered.
 *
 * ## Two-tier fixes, because this runs forever
 *
 * Watching for a departure has no ride to piggyback on, so the poll is
 * the entire battery cost of the feature. A GPS fix a minute, all day,
 * would be indefensible for something that spends most of its life
 * parked, so a sweep starts with the cheapest provider available.
 *
 * A coarse fix is usually enough - not to decide where we are, but to
 * decide whether the question is even close. GPS is powered up only when
 * the coarse fix disagrees with the containment we already believe, can't
 * prove that disagreement on its own, and something has actually moved -
 * see [shouldEscalate], which explains why all three conditions are
 * needed. Parked at home or driving across town, a network fix answers
 * it, however poor that fix happens to be.
 *
 * That matters more than it might look, because auto-created places get
 * PlaceResolver's 25 m placeholder radius - far smaller than a coarse
 * fix's error. Without the escalation a one-tier coarse poll wouldn't
 * merely be imprecise, it would be unable to resolve a small place at
 * all, and would fire essentially at random.
 *
 * In [Mode.ARRIVAL] there is no poll at all: a ride is already running
 * its own 1 Hz GPS stream, so the service feeds those fixes in through
 * [submit] and arrivals cost nothing.
 *
 * ## Why crossings are confirmed before they're believed
 *
 * A single fix landing on the wrong side of a boundary must not start or
 * end a ride - GPS wobble at the edge of a place would otherwise fire
 * one every time the vehicle sat near it. A candidate crossing has to
 * hold for [CONFIRM_WINDOW_MILLIS] *and* across at least
 * [CONFIRM_OBSERVATIONS] fixes before it's believed. Both conditions,
 * not either: time alone would let one stale outlier through after a
 * long gap, and count alone would let a 1 Hz ride stream confirm an
 * arrival in two seconds of driving past the driveway.
 *
 * The time half is waived for a *decisive* departure - two fixes running,
 * each more than [DECISIVE_MARGIN_METERS] beyond the boundary and beyond
 * their own accuracy. Two fixes are still required, so a stray reading
 * still cannot start a ride; what goes is only the minute of waiting,
 * which exists for the marginal case and was making every auto-start two
 * to three minutes late. Paired with [CANDIDATE_POLL_SECONDS], a real
 * departure now confirms about as fast as the vehicle reaches the end of
 * the street.
 *
 * The first observation after arming only seeds the current containment
 * and reports nothing. Sitting inside an auto-start place when watching
 * begins is the normal case - it's where the vehicle is parked - and it
 * is not a departure.
 *
 * ## Which fixes are allowed to count at all
 *
 * Both of those bars count *fixes*, so what qualifies as one matters
 * more than either threshold.
 *
 * A fix is only evidence about a boundary it can actually resolve. If
 * its accuracy radius straddles the edge - the distance to the boundary
 * is smaller than the error on that distance - it is equally consistent
 * with sitting still inside and with having left, and it is dropped:
 * it neither builds a candidate crossing nor abandons one. Parked in a
 * garage under a metal roof, three fixes in four were this kind, and
 * counting each as a verdict produced seventeen departures and
 * seventeen arrivals overnight from a vehicle that never moved.
 *
 * A departure additionally has to have gone somewhere. The fix that
 * puts the vehicle outside must also report it moving - not because
 * distance is untrustworthy, but because a receiver's *position* is the
 * part multipath corrupts, while its speed comes from the carrier
 * Doppler shift and stays near zero when the vehicle is standing still.
 * The night's one remaining false departure was a burst of fixes
 * claiming 74 m, then 90 m, then 91 m, each with 8-10 m of accuracy,
 * from a vehicle parked the whole time. Nothing about the distances
 * gives it away; the speed does. A fix that reports no speed is counted
 * as before, and standing outside for [STATIONARY_DEPARTURE_MILLIS]
 * confirms on distance alone, so a vehicle that left and then parked is
 * not stranded.
 *
 * A fix is also only evidence once. [LocationManager.getCurrentLocation]
 * may answer with a recent cached fix rather than a new measurement, and
 * over that same night a quarter of sweeps came back byte-identical to
 * the sweep before - which let one reading satisfy a bar deliberately
 * set at two. Fixes are matched on their own clock, not ours, so a
 * repeat is recognised however long the poll took to come round.
 */
class PlaceWatcher(
    context: Context,
    private val placeDao: PlaceDao,
    private val scope: CoroutineScope,
) {

    enum class Mode { DEPARTURE, ARRIVAL }

    /**
     * The watcher's own answer to "where am I", published so the ride
     * screen can show it. Distinct from the ride's GpsInfo, which
     * reports the ride's DistanceProvider and reads OFF whenever no ride
     * is running - precisely when this is the only thing that knows.
     */
    data class WatchFix(
        val latitude: Double,
        val longitude: Double,
        /** Negative when the fix didn't report one. */
        val accuracyMeters: Float,
        val provider: String,
        val geohash: String,
        val insidePlaceName: String?,
        val atMillis: Long,
    )

    sealed interface Crossing {
        val place: Place

        /**
         * Left a place flagged autoStart.
         *
         * [confirmedAt] and [leftAtMillis] are what a ride begun by this
         * departure needs to backdate itself. A crossing is never
         * reported at the moment it happens - it has to be confirmed
         * first, which takes two fixes and, unless the departure is
         * decisive, a minute of them agreeing - so by the time this is
         * emitted the vehicle has been driving for a while, and a ride
         * that starts metering here has already missed it.
         */
        data class Departed(
            override val place: Place,
            /** Where the fix that confirmed the departure put the vehicle. */
            val confirmedAt: RideLocation,
            /**
             * When the vehicle was first seen outside the place - the
             * earliest moment there is evidence it had gone. It left
             * somewhat before that, between this fix and the one before
             * it, but nothing here saw that happen.
             */
            val leftAtMillis: Long,
        ) : Crossing

        /** Reached a place flagged autoSave. */
        data class Arrived(override val place: Place) : Crossing
    }

    companion object {
        private const val TAG = "PlaceWatcher"

        private const val CONFIRM_WINDOW_MILLIS = 60_000L
        private const val CONFIRM_OBSERVATIONS = 2

        // While a crossing is suspected, poll this fast instead of at the
        // idle cadence. The expensive thing is checking constantly while
        // parked; checking hard for the half-minute after something looks
        // like it happened costs a handful of fixes and is what turns a
        // two-minute lag into about twenty seconds.
        private const val CANDIDATE_POLL_SECONDS = 15

        // How far beyond a boundary counts as "obviously across it,
        // whatever the accuracy" - enough that the time-based
        // confirmation can be waived. A car leaving clears this almost at
        // once; GPS wobble at the edge of a place never does.
        private const val DECISIVE_MARGIN_METERS = 100.0

        private const val COARSE_FIX_TIMEOUT_MILLIS = 30_000L
        private const val FINE_FIX_TIMEOUT_MILLIS = 30_000L

        // Floor for "the vehicle has actually moved", so that a place
        // with a large radius doesn't need a large movement to qualify.
        private const val MIN_MOVEMENT_METERS = 50.0

        // Walking pace, near enough. Below this a fix is not evidence that
        // a vehicle went anywhere.
        private const val MOVING_SPEED_MPS = 1.0f

        // ...but a vehicle really can leave and then stop - pull out, drive
        // fifty metres, park at the end of the road. After this long the
        // departure is believed on distance alone, stationary or not.
        private const val STATIONARY_DEPARTURE_MILLIS = 5 * 60_000L

        // Shortest gap between two GPS escalations while moving, and the
        // gap after which a standing disagreement earns one anyway.
        private const val ESCALATION_COOLDOWN_MILLIS = 3 * 60_000L
        private const val ESCALATION_STALE_MILLIS = 5 * 60_000L

        // What to assume when a fix won't say how accurate it is. Chosen
        // to be pessimistic: an unknown-accuracy fix escalates to GPS
        // rather than being trusted near a boundary.
        private const val ASSUMED_ACCURACY_METERS = 500f

        // Stands in when a fix won't report its accuracy at all - the
        // simulated provider, mainly. Displaying "±0 m" for that read as
        // a suspiciously perfect fix rather than a missing number.
        private const val UNKNOWN_ACCURACY_METERS = -1f

        // Broadcast the watcher sends itself when a sweep is due.
        private const val SWEEP_ACTION = "org.fossridemeter.app.PLACE_WATCHER_SWEEP"

        // Long enough for a sweep's worst case - a coarse fix that times
        // out, then a GPS escalation that times out too - plus slack. It
        // is a backstop, not a budget: the lock is released the moment
        // the sweep finishes.
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 90_000L

        // A fix that carries no clock of its own - the ride stream, which
        // is live by definition and never repeats an old reading.
        private const val NO_FIX_TIME = 0L

        private const val OUTSIDE = ""
    }

    private val appContext = context.applicationContext

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // Everything this class says is worth having after the fact - it
    // talks precisely when the phone can't be plugged into anything.
    private fun log(message: String) = EventLog.log(TAG, message)

    private val _crossings = MutableSharedFlow<Crossing>(extraBufferCapacity = 4)
    val crossings: SharedFlow<Crossing> = _crossings.asSharedFlow()

    private val _lastFix = MutableStateFlow<WatchFix?>(null)
    val lastFix: StateFlow<WatchFix?> = _lastFix.asStateFlow()

    private var pollJob: Job? = null
    private var placesJob: Job? = null

    private var idlePollSeconds: Int = 60
    private var watchAccuracy: WatchAccuracy = WatchAccuracy.GPS

    @Volatile
    private var allPlaces: List<Place> = emptyList()

    private var mode: Mode? = null

    // Confirmed containment: the flagged place we currently believe we're
    // inside, or null for "outside all of them". Null is a real answer
    // here, not a missing one - see armed.
    private var occupied: Place? = null

    // False until the first observation has seeded occupied. Until then
    // there is no previous side of the boundary, so nothing can be a
    // crossing.
    private var armed = false

    // Whether [occupied] rests on a fix precise enough to have actually
    // decided it. A ±1500 m fix "agrees" with any belief at all, so
    // agreement from one is not evidence - without tracking this, a
    // belief seeded badly is never revisited, because every later coarse
    // fix agrees with it and agreement suppresses escalation.
    private var occupancyConfirmed = false

    // Previous coarse fix, for deciding whether anything has moved, and
    // when GPS was last spun up.
    private var lastCoarse: Location? = null
    private var lastEscalation = 0L

    // The self-scheduling sweep alarm and the receiver it fires into.
    private var sweepAlarm: PendingIntent? = null
    private var sweepReceiver: BroadcastReceiver? = null

    // Which arming a sweep belongs to. Every registered receiver hears
    // the alarm broadcast, so a receiver that outlived its arming would
    // otherwise keep sweeping alongside the live one; each captures the
    // generation it was made in and stays quiet once that has moved on.
    private var sweepGeneration = 0

    // Timestamp of the last fix actually observed. getCurrentLocation is
    // allowed to answer with a recent cached fix, and does: over a night
    // parked, a quarter of sweeps came back byte-identical to the sweep
    // before. Counting that as a second observation lets one bad reading
    // clear a bar meant to need two.
    private var lastFixNanos = 0L

    // The position of that same last fix. A receiver with nothing new to
    // say re-emits its last measurement with a *fresh* timestamp, so the
    // timestamp alone cannot decide whether a fix is new evidence - see
    // the check in observe().
    private var lastLatitude = Double.NaN
    private var lastLongitude = Double.NaN
    private var lastAccuracy = Float.NaN

    // The candidate containment waiting to clear both confirmation bars.
    private var pendingKey: String? = null
    private var pendingSince = 0L
    private var pendingObservations = 0
    private var pendingDecisiveObservations = 0
    private var pendingMovingObservations = 0

    init {
        EventLog.init(context)
        placesJob = scope.launch {
            placeDao.getAll().collect { places ->
                allPlaces = places
            }
        }
    }

    /** Places that matter in the current mode. */
    private fun watchedPlaces(): List<Place> =
        when (mode) {
            Mode.DEPARTURE -> allPlaces.filter { it.autoStart }
            Mode.ARRIVAL -> allPlaces.filter { it.autoSave }
            null -> emptyList()
        }

    /**
     * Starts watching in [mode]. Restarts cleanly if already watching:
     * containment is forgotten, so the next observation re-seeds rather
     * than reporting a crossing against a stale side of the boundary.
     */
    @Synchronized
    fun start(mode: Mode, watchSeconds: Int, accuracy: WatchAccuracy) {

        log("Watching for $mode using $accuracy")

        stop()

        this.mode = mode
        occupied = null
        armed = false
        occupancyConfirmed = false

        // Forget which fix was last seen, so the first one after arming
        // is always taken. It only seeds which side of the boundary we
        // are on and can confirm nothing by itself, and a receiver
        // sitting on one position must not leave a freshly armed watcher
        // with no idea where it is.
        lastFixNanos = NO_FIX_TIME
        lastLatitude = Double.NaN
        lastLongitude = Double.NaN
        lastAccuracy = Float.NaN
        lastCoarse = null
        lastEscalation = 0L
        clearPending()

        // ARRIVAL is fed by the running ride's GPS stream through
        // submit() - polling as well would be paying twice for fixes the
        // ride is already collecting.
        idlePollSeconds = watchSeconds.coerceAtLeast(1)
        watchAccuracy = accuracy

        if (mode == Mode.DEPARTURE) {
            startSweeping()
        }
    }

    /**
     * Why the poll is an alarm and not a loop with a `delay()` in it.
     *
     * `delay()` schedules onto a timer that does not wake a sleeping
     * CPU. With the screen off and nothing else going on, the phone
     * suspends and the next sweep simply doesn't happen until something
     * unrelated wakes the device - which is why a poll configured for
     * 60 s measured a median of 60 s and a *worst case of 32 minutes*
     * over one night parked. The tail is the whole problem: it is
     * exactly the half hour in which a departure goes unnoticed. Being a
     * foreground service doesn't help, because that keeps the process
     * alive, not the processor awake.
     *
     * An `ELAPSED_REALTIME_WAKEUP` alarm does wake it. The alarm holds
     * the CPU up only for the duration of `onReceive`, which returns
     * immediately here, so the sweep itself runs under a wake lock of
     * its own and releases it as soon as the fix is in.
     *
     * Each sweep schedules the next one rather than repeating: the
     * interval depends on whether a crossing is pending, and a
     * self-scheduling alarm can't drift into a pile-up if a sweep runs
     * long.
     */
    private fun startSweeping() {

        val generation = ++sweepGeneration

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onSweepDue(generation)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(SWEEP_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sweepReceiver = receiver

        sweepAlarm = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(SWEEP_ACTION).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Which of the three regimes we're actually in, recorded once per
        // arming so a log with a gap in it says why. Exact alarms are
        // only reliable in Doze for an app the user has taken off battery
        // optimisation; without that the system may hold even an
        // allow-while-idle alarm for a quarter of an hour.
        log("Sweeping by alarm: exact=${canScheduleExact()}, " +
                "unrestricted=${
                    powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
                }"
        )

        onSweepDue(generation)
    }

    /**
     * One sweep, awake, followed by the alarm for the next one.
     *
     * [generation] is the arming the calling receiver was registered in.
     * A broadcast reaches *every* registered receiver, so one left behind
     * by an arming that has since been replaced would sweep in parallel
     * with the live one - two fixes an interval instead of one, and,
     * since they share pollJob, each cancelling the other's fix
     * mid-request. That is what "getCurrentLocation failed - The
     * operation has been canceled" was, and what made a parked phone
     * take 2.15 fixes a minute on a 60-second setting.
     */
    private fun onSweepDue(generation: Int) {

        if (generation != sweepGeneration) {
            log("Ignoring sweep from a stale receiver ($generation, now $sweepGeneration)")
            return
        }

        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FossRideMeter:sweep",
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)

        pollJob?.cancel()
        pollJob = scope.launch {
            try {
                sweep()
            } finally {
                scheduleNextSweep()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private fun scheduleNextSweep() {

        val alarm = sweepAlarm ?: return

        // Fast while something is pending, idle otherwise.
        val seconds =
            if (pendingKey != null) {
                minOf(CANDIDATE_POLL_SECONDS, idlePollSeconds)
            } else {
                idlePollSeconds
            }

        val dueAt = SystemClock.elapsedRealtime() + seconds * 1000L

        // setExactAndAllowWhileIdle is the one that keeps time overnight.
        // Without permission for it the inexact form still wakes the
        // device, just when the system finds it convenient - later than
        // asked, but not the arbitrary wait of a timer that never fires
        // at all.
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, dueAt, alarm
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, dueAt, alarm
            )
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    private fun stopSweeping() {

        // Order matters: every sweep schedules the next one from its
        // `finally`, so cancelling the job below would put the alarm
        // straight back if there were still one to put back. Dropping
        // the PendingIntent first is what makes that a no-op.
        sweepAlarm?.let { alarmManager.cancel(it) }
        sweepAlarm = null

        sweepReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        sweepReceiver = null

        // Anything still holding the old generation - a broadcast already
        // in flight, a receiver that failed to unregister - is now stale.
        sweepGeneration++

        pollJob?.cancel()
        pollJob = null
    }

    @Synchronized
    fun stop() {
        stopSweeping()
        mode = null
        occupied = null
        armed = false
        clearPending()
    }

    fun close() {
        stop()
        placesJob?.cancel()
        placesJob = null
    }

    /**
     * Feeds in a fix the running ride already paid for (ARRIVAL mode),
     * along with the ride's own reported accuracy - which is real, and a
     * good deal better than a sweep's, since the ride is running GPS at
     * 1 Hz rather than the cheapest provider once a minute.
     */
    fun submit(location: RideLocation, accuracyMeters: Float) {
        observe(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (accuracyMeters > 0f) accuracyMeters else UNKNOWN_ACCURACY_METERS,
            provider = "ride",
            fixNanos = NO_FIX_TIME,
            speedMps = null,
        )
    }

    /**
     * One poll: cheapest usable fix, escalated to GPS only when that fix
     * can't answer the question and something has actually happened.
     */
    private suspend fun sweep() {

        val watched = watchedPlaces()
        if (watched.isEmpty()) return

        if (watchAccuracy == WatchAccuracy.GPS) {
            sweepDirect(watched)
            return
        }

        val coarseProvider = coarseProvider()
        if (coarseProvider == null) {
            log("WARN: No location provider is enabled - cannot watch")
            return
        }

        val coarse = awaitFix(coarseProvider, COARSE_FIX_TIMEOUT_MILLIS)
        if (coarse == null) {
            // The usual cause is having only while-in-use location
            // permission: the service runs, the notification shows, and
            // every fix silently comes back empty in the background.
            log("WARN: No fix from $coarseProvider within ${COARSE_FIX_TIMEOUT_MILLIS}ms")
            return
        }

        val escalate = shouldEscalate(coarse, watched)
        lastCoarse = coarse

        val fix =
            if (escalate) {
                lastEscalation = System.currentTimeMillis()
                log("Coarse fix can't settle a possible crossing, escalating to GPS")
                awaitFix(LocationManager.GPS_PROVIDER, FINE_FIX_TIMEOUT_MILLIS) ?: coarse
            } else {
                coarse
            }

        log("Sweep: ${fix.provider} ${fix.latitude},${fix.longitude} " +
                "accuracy=${accuracyOf(fix)}m " +
                "speed=${speedOf(fix)?.let { "%.1fm/s".format(it) } ?: "none"} " +
                "escalated=$escalate"
        )

        observe(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracy = accuracyOf(fix),
            provider = fix.provider ?: "unknown",
            fixNanos = fix.elapsedRealtimeNanos,
            speedMps = speedOf(fix),
        )
    }

    /**
     * One fix, asked for properly, with no tier below it to fall back on.
     *
     * Nothing here needs the escalation machinery: a GPS fix resolves a
     * place outright, so containment is decided the moment it arrives
     * rather than several sweeps later. That directness is the whole
     * point of the setting - the tiered path's delay was costing more
     * than its battery saving was worth.
     */
    private suspend fun sweepDirect(watched: List<Place>) {

        val fix = awaitFix(LocationManager.GPS_PROVIDER, FINE_FIX_TIMEOUT_MILLIS)
        if (fix == null) {
            log("WARN: no GPS fix within ${FINE_FIX_TIMEOUT_MILLIS}ms")
            return
        }

        lastCoarse = fix

        log(
            "Sweep: ${fix.provider} ${fix.latitude},${fix.longitude} " +
                "fixAccuracy=${accuracyOf(fix)}m " +
                "speed=${speedOf(fix)?.let { "%.1fm/s".format(it) } ?: "none"} direct" +
                distancesTo(fix, watched)
        )

        observe(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracy = accuracyOf(fix),
            provider = fix.provider ?: "gps",
            fixNanos = fix.elapsedRealtimeNanos,
            speedMps = speedOf(fix),
        )
    }

    /**
     * " | Home d=142m r=61m OUTSIDE" for each watched place - the actual
     * inputs to the containment test, so a log never again leaves it
     * unclear whether a number is a distance or an accuracy, or why a
     * place did or didn't contain the fix.
     */
    private fun distancesTo(fix: Location, watched: List<Place>): String =
        distancesTo(fix.latitude, fix.longitude, watched)

    private fun distancesTo(
        latitude: Double,
        longitude: Double,
        watched: List<Place>,
    ): String =
        watched.joinToString("") { place ->
            val distance = DistanceUtil.haversineMeters(
                latitude, longitude,
                place.latitude, place.longitude
            )
            val inside = if (distance <= place.radiusMeters) "INSIDE" else "OUTSIDE"
            " | ${place.name} d=%.0fm r=%.0fm $inside".format(distance, place.radiusMeters)
        }

    /**
     * Whether this sweep is worth a GPS fix.
     *
     * The first version of this asked only whether the coarse fix's error
     * radius reached across a boundary, and that was close to the exact
     * opposite of what's wanted: a *poor* fix has a huge error radius, so
     * it straddles everything, so weak signal - the one condition where
     * GPS is slowest and most expensive - guaranteed an escalation every
     * single sweep, all night, parked in the driveway.
     *
     * Three things now have to hold together:
     *
     * 1. **The coarse fix disagrees with what we already believe.** If it
     *    puts us where we already think we are, there is nothing to
     *    resolve and no accuracy figure makes that interesting.
     * 2. **It can't settle that disagreement itself.** If its whole error
     *    radius lies on the far side of the boundary, it has already
     *    proved the crossing and GPS would only agree with it.
     * 3. **Something has moved, or the disagreement has stood a long
     *    time.** A boundary crossing requires going somewhere. Fixes
     *    jittering inside their own error radius are a parked vehicle, so
     *    movement is measured against that error radius rather than a
     *    fixed distance. The staleness escape hatch exists so a genuine
     *    crossing that happens to be followed by sitting still - drive
     *    away, park, signal poor - is still resolved eventually.
     */
    private fun shouldEscalate(fix: Location, watched: List<Place>): Boolean {

        val accuracy = accuracyOf(fix)

        // Not armed yet, so this fix decides the side we start on and
        // everything afterwards is measured as a change from it. Getting
        // it wrong is not a near miss, it's silent permanent failure:
        // seed "outside" while actually parked in the drive and then
        // leaving isn't a change at all, so the departure never fires -
        // and no later fix corrects it, because every one of them agrees
        // with the wrong answer.
        //
        // So if the coarse fix can't resolve containment for some watched
        // place, spend a GPS fix on it. This happens once per arming,
        // and it is the one moment accuracy genuinely decides the outcome.
        if (!armed) {
            return watched.any { place ->
                val distance = DistanceUtil.haversineMeters(
                    fix.latitude, fix.longitude,
                    place.latitude, place.longitude
                )
                abs(distance - place.radiusMeters) <= accuracy
            }
        }

        val inside = watched.firstOrNull { place ->
            DistanceUtil.haversineMeters(
                fix.latitude, fix.longitude,
                place.latitude, place.longitude
            ) <= place.radiusMeters
        }

        // (1) Agrees with what we already believe. Normally nothing to
        // resolve - but only if the belief was worth something in the
        // first place. Until a fix precise enough to decide it has done
        // so, agreement is just two vague fixes nodding at each other,
        // and waiting for a disagreement that can never come is how a
        // badly seeded belief survives indefinitely.
        if ((inside?.id ?: OUTSIDE) == (occupied?.id ?: OUTSIDE)) {
            if (occupancyConfirmed) return false
            val sinceLastEscalation = System.currentTimeMillis() - lastEscalation
            return sinceLastEscalation >= ESCALATION_COOLDOWN_MILLIS
        }

        // (2) can the coarse fix prove the crossing on its own?
        val boundary = inside ?: occupied ?: return false
        val distance = DistanceUtil.haversineMeters(
            fix.latitude, fix.longitude,
            boundary.latitude, boundary.longitude
        )
        val conclusive = abs(distance - boundary.radiusMeters) > accuracy
        if (conclusive) return false

        // (3) movement, or a disagreement that has stood long enough.
        val now = System.currentTimeMillis()
        val moved = lastCoarse?.let { previous ->
            DistanceUtil.haversineMeters(
                previous.latitude, previous.longitude,
                fix.latitude, fix.longitude
            ) > maxOf(accuracy.toDouble(), MIN_MOVEMENT_METERS)
        } ?: true

        val sinceLast = now - lastEscalation
        return if (moved) {
            sinceLast >= ESCALATION_COOLDOWN_MILLIS
        } else {
            sinceLast >= ESCALATION_STALE_MILLIS
        }
    }

    private fun accuracyOf(fix: Location): Float =
        if (fix.hasAccuracy()) fix.accuracy else ASSUMED_ACCURACY_METERS

    /**
     * Cheapest provider that's actually enabled. FUSED is the platform's
     * own (not Play Services') and is the least costly where it exists;
     * NETWORK next; GPS only as a last resort, which on a device with
     * location off entirely will simply never return a fix.
     */
    /**
     * The fix's own speed, or null if it didn't report one.
     *
     * This is the number that catches a lying fix. GNSS speed is derived
     * from the carrier Doppler shift rather than by differencing
     * positions, so it stays near zero for a receiver that is sitting
     * still even while multipath walks its *position* ninety metres down
     * the road - which is exactly the failure this watcher could not
     * otherwise see. It costs nothing: the value is already aboard the
     * fix that was going to be taken anyway.
     */
    private fun speedOf(fix: Location): Float? =
        if (fix.hasSpeed()) fix.speed else null

    private fun coarseProvider(): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    /**
     * A single fix from [provider], or null if none arrives in time.
     *
     * getCurrentLocation() is the modern one-shot and is used where it
     * exists; below API 30 the equivalent is a normal update request torn
     * down after the first result.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(provider: String, timeoutMillis: Long): Location? =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    awaitFixModern(provider, continuation)
                } else {
                    awaitFixLegacy(provider, continuation)
                }
            }
        }

    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun awaitFixModern(
        provider: String,
        continuation: CancellableContinuation<Location?>,
    ) {
        val signal = android.os.CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        runCatching {
            locationManager.getCurrentLocation(
                provider,
                signal,
                Runnable::run,
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }.onFailure {
            log("WARN: getCurrentLocation failed for $provider - ${it.message}")
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun awaitFixLegacy(
        provider: String,
        continuation: CancellableContinuation<Location?>,
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location)
            }

            @Deprecated("Required by LocationListener on API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
            }

            override fun onProviderDisabled(provider: String) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onProviderEnabled(provider: String) {}
        }

        continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }

        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                0L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }.onFailure {
            log("WARN: requestLocationUpdates failed for $provider - ${it.message}")
            if (continuation.isActive) continuation.resume(null)
        }
    }

    /**
     * The state machine. See the class comment for why a crossing has to
     * be confirmed twice over before it's believed, and why the first
     * observation never reports one.
     */
    private fun observe(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        provider: String,
        fixNanos: Long,
        speedMps: Float?,
    ) {

        val watched = watchedPlaces()
        if (watched.isEmpty()) return

        val current = watched.firstOrNull { place ->
            DistanceUtil.haversineMeters(
                latitude, longitude,
                place.latitude, place.longitude
            ) <= place.radiusMeters
        }

        _lastFix.value = WatchFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            provider = provider,
            geohash = GeohashUtil.encode(latitude, longitude, 8),
            insidePlaceName = current?.name,
            atMillis = System.currentTimeMillis(),
        )

        // The same fix handed back twice is one measurement, not two,
        // and confirmation counts measurements.
        //
        // Matching timestamps were the whole test, and that let a false
        // auto-start through at 03:57 on 2026-08-21: parked overnight,
        // one stray fix landed 82 m from a 30 m place, and the receiver
        // then re-emitted that exact position - same latitude, same
        // longitude, same accuracy - every half minute with a new
        // timestamp. Three "separate" observations of one bad reading
        // cleared both confirmation bars and started a ride on a car
        // that never moved. It ran for hours at 0.00 miles.
        //
        // So identity is the measurement itself. Two fixes agreeing to
        // the last bit of a double are one fix replayed; a stationary
        // receiver that is genuinely still measuring jitters in the
        // seventh decimal and never repeats exactly.
        val sameFix =
            (fixNanos != NO_FIX_TIME && fixNanos == lastFixNanos) ||
                (latitude == lastLatitude &&
                    longitude == lastLongitude &&
                    accuracy == lastAccuracy)

        if (sameFix) {
            log(
                "Ignoring repeat of the fix already observed " +
                    "($latitude,$longitude accuracy=${accuracy}m) - no new evidence"
            )
            return
        }

        lastFixNanos = fixNanos
        lastLatitude = latitude
        lastLongitude = longitude
        lastAccuracy = accuracy

        // Could this fix actually tell inside from outside for every
        // place being watched? If so, whatever it says about containment
        // is worth believing, and the belief no longer needs revisiting
        // while it holds.
        val resolved = watched.none { place ->
            val distance = DistanceUtil.haversineMeters(
                latitude, longitude,
                place.latitude, place.longitude
            )
            abs(distance - place.radiusMeters) <= accuracy
        }
        if (resolved) occupancyConfirmed = true

        // ...and if it can't, it is not evidence of anything. A fix whose
        // uncertainty straddles the boundary is as consistent with sitting
        // still inside as with having left, so it neither builds a
        // candidate crossing nor abandons one - it is simply not counted.
        //
        // This is the difference between watching a boundary and watching
        // a number cross it. Parked in a garage overnight, three fixes in
        // four were of exactly this kind, and treating each as a verdict
        // was what produced a night of departures and arrivals from a
        // vehicle that never moved.
        if (!resolved) {
            log("Fix can't resolve the boundary (accuracy ${accuracy}m)" +
                    distancesTo(latitude, longitude, watched) +
                    " - not counted"
            )
            return
        }

        val key = current?.id ?: OUTSIDE
        val occupiedKey = occupied?.id ?: OUTSIDE

        if (!armed) {
            occupied = current
            armed = true
            occupancyConfirmed = resolved
            log("Armed $mode at $latitude,$longitude (accuracy ${accuracy}m), " +
                    "inside=${current?.name ?: "nothing"}, " +
                    "watching ${watched.size} place(s)"
            )
            return
        }

        if (key == occupiedKey) {
            // Back on the side we already believed - whatever candidate
            // was building was noise.
            if (pendingKey != null) {
                log("Candidate crossing abandoned, back inside ${occupied?.name ?: "nothing"}")
            }
            clearPending()
            return
        }

        val now = System.currentTimeMillis()

        // Is this fix *obviously* on the far side, or only just across?
        // Only a departure may skip the wait on that basis: a car that
        // has left is far away almost immediately, whereas driving
        // straight through a large place would look equally "obviously
        // inside" it, and fast-confirming that would end a ride at a
        // place the user never stopped at.
        val decisive = mode == Mode.DEPARTURE &&
            current == null &&
            // The fix must itself be at least as precise as the margin it
            // is clearing. Without this, a cell-tower fix reporting
            // ±1500 m clears a ±1500 m threshold by being wrong in a
            // consistent direction - and network positioning is offset,
            // not random, so it can be wrong the same way twice running
            // and fast-confirm a departure from a car that never moved.
            // An imprecise fix falls back to the timed path, where a
            // minute of continued disagreement, and an escalation to GPS,
            // sort it out properly.
            accuracy > 0f && accuracy <= DECISIVE_MARGIN_METERS &&
            occupied?.let { place ->
                val distance = DistanceUtil.haversineMeters(
                    latitude, longitude,
                    place.latitude, place.longitude
                )
                distance - place.radiusMeters >
                    maxOf(accuracy.toDouble(), DECISIVE_MARGIN_METERS)
            } == true

        // Did the fix that saw us on the far side also see us going
        // anywhere? A departure is a vehicle leaving; a fix reporting
        // walking pace or less, from ninety metres down a road nobody
        // drove, is the receiver's position wandering and not a journey.
        // Fixes that decline to report a speed at all are counted, so a
        // device that doesn't supply one behaves exactly as before.
        val moving = mode != Mode.DEPARTURE ||
            speedMps == null ||
            speedMps >= MOVING_SPEED_MPS

        if (key != pendingKey) {
            pendingKey = key
            pendingSince = now
            pendingObservations = 1
            pendingDecisiveObservations = if (decisive) 1 else 0
            pendingMovingObservations = if (moving) 1 else 0
            return
        }

        pendingObservations++
        if (decisive) pendingDecisiveObservations++
        if (moving) pendingMovingObservations++

        val held = now - pendingSince

        // Two fixes are always required - one stray reading must never
        // start a ride. What the decisive case skips is only the *time*
        // those two have to be spread over, which exists to stop jitter
        // at a boundary confirming itself. Jitter is not 100 m past the
        // edge twice running.
        val enoughObservations = pendingObservations >= CONFIRM_OBSERVATIONS
        val settled = held >= CONFIRM_WINDOW_MILLIS ||
            pendingDecisiveObservations >= CONFIRM_OBSERVATIONS

        // Departing takes movement to go with the distance - or long
        // enough standing outside that a stopped vehicle is the only
        // remaining explanation. Arrivals never ask: arriving is
        // stopping.
        val went = pendingMovingObservations >= CONFIRM_OBSERVATIONS ||
            held >= STATIONARY_DEPARTURE_MILLIS

        if (!enoughObservations || !settled || !went) {
            log("Candidate ${current?.name ?: "outside"} held ${held}ms " +
                    "over $pendingObservations fix(es) " +
                    "($pendingDecisiveObservations decisive, " +
                    "$pendingMovingObservations moving) - not confirmed yet"
            )
            return
        }

        val previous = occupied
        val observations = pendingObservations

        // Read before clearPending() drops it: this is when the vehicle
        // was first seen on the far side, and a ride started by this
        // departure dates itself from it rather than from now.
        val leftAt = pendingSince

        occupied = current
        clearPending()

        val crossing = when (mode) {
            Mode.DEPARTURE -> previous?.let {
                Crossing.Departed(
                    place = it,
                    confirmedAt = RideLocation(latitude, longitude),
                    leftAtMillis = leftAt,
                )
            }
            Mode.ARRIVAL -> current?.let { Crossing.Arrived(it) }
            null -> null
        } ?: return

        log("Confirmed $crossing after ${held}ms over $observations fix(es), " +
                "accuracy=${accuracy}m"
        )
        scope.launch { _crossings.emit(crossing) }
    }

    private fun clearPending() {
        pendingKey = null
        pendingSince = 0L
        pendingObservations = 0
        pendingDecisiveObservations = 0
        pendingMovingObservations = 0
    }
}
