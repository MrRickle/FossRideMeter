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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.data.RideRepository
import org.fossridemeter.app.data.PlaceResolver
import org.fossridemeter.app.data.PlaceLocationRecalculator
import org.fossridemeter.app.data.LiveRideStore
import org.fossridemeter.app.data.StopDao
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Stop
import org.fossridemeter.app.model.toRecord
import org.fossridemeter.app.util.DistanceUtil
import org.fossridemeter.app.util.EventLog
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

class RideMeter(
    private val timeProvider: TimeProvider,
    private val createDistanceProvider: (Settings) -> DistanceProvider,
    private val rideRepository: RideRepository,
    private val placeResolver: PlaceResolver,
    private val placeLocationRecalculator: PlaceLocationRecalculator,
    private val stopDao: StopDao,
    private val liveRideStore: LiveRideStore,
    // Every action sounds from here rather than from the button that
    // caused it, so an automatic start is as audible as a tapped one -
    // which is the case that wanted a sound in the first place.
    private val sounds: RideSounds,
) {

    companion object {
        private const val STOP_DISTANCE_THRESHOLD_METERS = 30.0

        // How far back start() will believe a departure happened. A
        // confirmation is late by a poll or two, and a candidate held
        // against a stationary vehicle can take five minutes to settle;
        // anything beyond this is a clock or a caller in error.
        private const val MAX_BACKDATE_MILLIS = 15 * 60_000L

        // How long a gap an interrupted ride will resume across.
        //
        // An ordinary memory kill doesn't come near this: the service is
        // START_STICKY, so the system restarts it in seconds and the ride
        // picks up almost at once. What produces a long gap is a
        // different kind of event - a force stop or a swipe off Recents,
        // which cancels the sticky restart; a flat battery; a reboot.
        // Those can be hours, and resuming across one would meter a
        // vehicle that was parked for the evening.
        //
        // An hour is deliberately generous with the ambiguous middle,
        // because the common case recovers in seconds and anything near
        // the boundary is more likely a phone that struggled than a ride
        // that ended. Past it the ride comes back paused, says why, and
        // offers Resume and Save on the notification - so being wrong in
        // that direction costs one tap.
        private const val MAX_GAP_TO_RESUME_MILLIS = 60 * 60_000L
        private val PERSIST_INTERVAL = 5.seconds
    }

    // Whether the ride's name is still the one this class made up, and
    // so may be rewritten as more of the ride becomes known. The moment
    // the user edits it, it is theirs and nothing here touches it
    // again.
    private var nameIsAutomatic = false

    fun updateName(name: String) {
        nameIsAutomatic = false
        _ride.value = _ride.value.copy(name = name)
    }

    fun updateManualAmount(amount: Double?) {
        _ride.value = _ride.value.copy(manualAmount = amount)
    }

    private val _ride = MutableStateFlow(Ride())
    val ride: StateFlow<Ride> = _ride.asStateFlow()

    private val _gpsInfo =
        MutableStateFlow(GpsInfo())

    val gpsInfo: StateFlow<GpsInfo> =
        _gpsInfo.asStateFlow()

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private var timeJob: Job? = null
    private var distanceJob: Job? = null
    private var persistJob: Job? = null
    private var startPlaceJob: Job? = null

    private var distanceProvider: DistanceProvider? = null

    // Stop detection: an "anchor" point + when we first stopped there.
    // While each new GPS fix stays within STOP_DISTANCE_THRESHOLD_METERS
    // of the anchor, dwell time keeps accumulating without moving the
    // anchor (a fixed anchor, not a rolling one, so slow GPS drift can't
    // repeatedly reset the clock). Once a fix lands outside that radius,
    // movement has resumed - if the dwell at the old anchor met
    // activeSettings.stopDetectionMinutes, it's finalized as a Stop right
    // then: place resolved, row written, place average recomputed, all at
    // detection time.
    private var dwellAnchor: RideLocation? = null
    private var dwellAnchorTime: Long = 0L

    // The Stop already written for the dwell currently under way, if the
    // user recorded it by hand before it ended. One dwell is at most
    // one stop row: sitting somewhere is a single visit however many
    // ways it gets noticed, and a second row for it is a duplicate in
    // the stops list. It's held rather than just flagged because the
    // dwell's real end time isn't known until the vehicle moves off,
    // which is after the row was written - that row gets the departure
    // time then, instead of the moment the button was pressed.
    private var dwellStop: Stop? = null

    // The stop for the visit currently under way, and the place it is
    // at, while the vehicle is still inside that place.
    //
    // A stop is written when a dwell ends, and a dwell ends whenever the
    // vehicle moves more than STOP_DISTANCE_THRESHOLD_METERS from its
    // anchor. Inside somewhere large - a store's car park, a yard - that
    // happens repeatedly without leaving at all: park, go in, come out,
    // move the truck, go back in. Each of those used to be its own stop,
    // so one visit to Home Depot could be seven rows. A real ride on
    // 2026-09-05 recorded eight, seven of them at one place.
    //
    // So while the vehicle stays inside the place, the visit's stop is
    // extended rather than a new one written. Leaving the place closes
    // it, which is what stops a second visit later in the same ride from
    // being welded onto the first.
    private var openVisit: Stop? = null
    private var openVisitPlace: Place? = null

    // When the ride was paused, if it is. A dwell under way must not go
    // on accruing stopped time while the meter isn't running - a user
    // who pauses for lunch inside the dwell radius would come back to an
    // hour of "stopped" time the ride's own clock never counted. resume()
    // shifts dwellAnchorTime forward by this gap, which keeps both the
    // stopped-time total and the stop-detection threshold measuring ride
    // time rather than wall time.
    private var dwellPausedAt: Long = 0L

    // Mirrors the Stop rows already written for this ride, in sequence
    // order. It is a cache of what's in the database, not a pending
    // batch - it exists so numbering the next stop and checking the
    // trailing one at save() don't need a query.
    private val recordedStops = mutableListOf<Stop>()
    private var activeSettings: Settings = Settings()

    // What the ride had already covered before these providers started
    // counting. The providers count from zero and their totals *replace*
    // the ride's, which is right while one instance meters the whole ride
    // - pause() never resets them, so resuming continues their running
    // totals - but two kinds of ride begin part-way through:
    //
    // * one recovered after the process died, whose providers did not
    //   survive to be continued (restore());
    // * one an automatic start backdates, which was already driving
    //   before anything confirmed it had left (start()).
    //
    // Both hold what came before here, and it is added back to whatever
    // the providers go on to count. Zero for a ride started by hand.
    private var priorMeters = 0.0
    private var priorSeconds = 0L

    /**
     * The name a ride carries until the user types something else.
     *
     * A ride is named the moment it starts, not when it is saved, because
     * an unnamed running ride is the one on screen and "" is no way to
     * refer to it. The date is short because the ride's own info section
     * already carries the full start time; what the name adds is where
     * this ride went.
     *
     * The start place is not known at the instant a manual ride starts -
     * it takes a fix, and then a resolve - so until it arrives the name
     * falls back to the time, which always exists. [endPlaceName] is only
     * folded in at save: while a ride runs, the end place is whatever
     * stop it last passed through, and a name that rewrote itself at
     * every stop would be noise.
     */
    private fun automaticName(includeEnd: Boolean): String {

        val started = Instant.ofEpochMilli(_ride.value.startTime)
            .atZone(ZoneId.systemDefault())

        val date = started.format(DateTimeFormatter.ofPattern("MM/dd"))
        val start = _ride.value.startPlaceName
            ?: return "$date ${started.format(DateTimeFormatter.ofPattern("h:mm a"))}"

        val end = _ride.value.endPlaceName.takeIf { includeEnd }
        return if (end == null) "$date $start ->" else "$date $start -> $end"
    }

    /**
     * [startPlace], when given, is the place the ride is known to be
     * starting from - the one an automatic start just detected the
     * vehicle leaving.
     *
     * It matters because by the time a departure is confirmed the vehicle
     * is, by definition, no longer there: the first GPS fix of the ride
     * lands somewhere down the road, and resolving *that* would create a
     * brand new place a block from the one the user actually left, then
     * link the ride to it. So the place is passed in and its own
     * coordinates become the ride's start point, which is also what stops
     * PlaceBoundaryEnforcer from later detaching a start point that sits
     * outside the place it claims.
     *
     * Averaging is unaffected: a place's location is the mean of its
     * linked points, and adding a point equal to that mean leaves it
     * where it was.
     *
     * [startedAtMillis] and [metersAlready] are the rest of that same
     * lag, and they exist because fixing only the start *place* left the
     * ride claiming to have begun at the place, now, having travelled
     * nothing - while the odometer and the clock actually began wherever
     * the confirming fix landed. The drive between the two was silently
     * unbilled, and the ride's own start location was a point it was
     * never metered from.
     *
     * So the ride is dated from when the vehicle was seen to leave, and
     * seeded with the distance from the place to where it had got to.
     * That distance is a straight line and so slightly short of the road
     * actually driven; a minute of unmetered driving is the alternative,
     * and it is short by metres rather than by the whole leg.
     *
     * [startedAtMillis] is clamped into the recent past. Nothing should
     * ever hand this a departure from an hour ago, and a ride whose clock
     * starts an hour behind bills an hour nobody drove - so a value that
     * far out is treated as a bad reading rather than believed.
     */
    fun start(
        settings: Settings,
        startPlace: Place? = null,
        startedAtMillis: Long = System.currentTimeMillis(),
        metersAlready: Double = 0.0,
    ) {

        Log.d(
            "RideMeter",
            "Start Tracking, startPlace=${startPlace?.name}, " +
                "backdateRequested=${System.currentTimeMillis() - startedAtMillis}ms, " +
                "metersAlready=$metersAlready"
        )

        stopTracking()

        timeProvider.reset()
        distanceProvider = createDistanceProvider(settings)
        activeSettings = settings

        dwellAnchor = null
        dwellAnchorTime = 0L
        dwellStop = null
        dwellPausedAt = 0L
        recordedStops.clear()
        openVisit = null
        openVisitPlace = null

        val now = System.currentTimeMillis()
        val startedAt = startedAtMillis.coerceIn(now - MAX_BACKDATE_MILLIS, now)

        priorMeters = metersAlready.coerceAtLeast(0.0)
        priorSeconds = (now - startedAt) / 1000L

        // Whatever the user typed before pressing start belongs to the
        // ride about to begin: a name for the run, or a fare already
        // agreed. The READY ride is not a real ride - it has no row and
        // an INVALID id - so these two fields are the only thing on it
        // worth keeping, and building a fresh Ride here would otherwise
        // silently throw them away at the moment of starting.
        val pendingName = _ride.value.name
        val pendingManualAmount = _ride.value.manualAmount

        _ride.value = Ride(
            id = UUID.randomUUID().toString(),
            name = pendingName,
            manualAmount = pendingManualAmount,
            status = RideStatus.RUNNING,
            startTime = startedAt,
            // Seeded here rather than left at zero for the providers to
            // fill in: the ride is on screen from this instant, and the
            // first frame of a backdated ride showing 0.0 for a second
            // is the bug this was meant to fix, in miniature.
            meters = priorMeters,
            elapsedSeconds = priorSeconds,
            startLocation = startPlace
                ?.let { RideLocation(it.latitude, it.longitude) }
                ?: gpsInfo.value.rideLocation,
            startPlaceId = startPlace?.id,
            startPlaceName = startPlace?.name,
        )

        // Whatever the user typed stands; otherwise the ride gets its
        // automatic name here, so it has one from its first frame on
        // screen rather than acquiring one at save.
        nameIsAutomatic = pendingName.isBlank()
        if (nameIsAutomatic) {
            _ride.value = _ride.value.copy(name = automaticName(includeEnd = false))
        }

        // The ride's row exists in the database from the moment it
        // starts, with whatever is known so far - not just once it's
        // finished/saved. Everything not yet known (end time/location/
        // place, final distance/duration/amount) is written as a
        // placeholder here and overwritten as it becomes real.
        val startRide = _ride.value

        // Written before the row, not after: the marker is what says a
        // ride was in progress if this process doesn't survive, and a
        // row with no marker reads as a finished ride.
        liveRideStore.markLive(startRide.id, RideStatus.RUNNING)

        scope.launch {
            rideRepository.saveRide(
                startRide.toRecord(
                    settings = activeSettings,
                    amount = 0.0,
                )
            )
        }

        startTimeTracking()
        startDistanceTracking()
        startPersisting()

        sounds.play(RideSounds.Sound.START)
    }

    /**
     * Brings back a ride this process never started - one that was being
     * metered when the previous process died, which the system does
     * without warning and, on a phone under memory pressure, without
     * anything being wrong. See LiveRideStore for how it is recognised.
     *
     * It comes back **PAUSED**, never RUNNING. Between the death and now
     * the vehicle may have driven fifty miles or sat still, and nothing
     * recorded which: the providers stopped with the process. Resuming
     * metering would quietly bill a gap this app knows nothing about,
     * where pausing states exactly what is known - the ride ran to its
     * last persisted moment - and leaves the user to resume it or save
     * it. A paused ride is already complete and saveable, which is the
     * whole reason there is no FINISHED state.
     *
     * [settings] supplies the fields a RideRecord doesn't carry; the
     * rates come from the record itself, because the rates a ride was
     * metered at belong to that ride and must survive the restart along
     * with it.
     *
     * Refuses to touch a live ride: this runs at service create, where
     * the meter is fresh, but a second call must never overwrite a ride
     * in progress.
     */
    fun restore(
        record: RideRecord,
        stops: List<Stop>,
        stopPlaceIds: List<String>,
        stopPlaceNames: List<String>,
        startPlaceName: String?,
        endPlaceName: String?,
        settings: Settings,
        wasRunning: Boolean = false,
    ): Restored? {

        if (_ride.value.status != RideStatus.READY) return null

        activeSettings = settings.copy(
            perMeterRate = record.perMeterRate,
            hourlyRate = record.hourlyRate,
            baseAmount = record.baseAmount,
            minimumAmount = record.minimumAmount,
            distanceProvider = record.distanceProvider,
        )

        // Everything the ride had already covered. The providers will
        // count from zero if it is resumed, so this is what they get
        // added to.
        priorMeters = record.meters
        priorSeconds = record.elapsedSeconds

        // The gap: from the last moment the ride is known to have been
        // metering, to now. Nothing was measured across it - the process
        // was dead - so the distance driven in it is gone whatever
        // happens next. The time is not: the app knows exactly how long
        // it was away, and what the ride was doing when it went.
        val lastKnownLive = record.startTime + record.elapsedSeconds * 1000L
        val gapMillis = (System.currentTimeMillis() - lastKnownLive).coerceAtLeast(0L)

        // A ride that was running comes back running, and the gap counts
        // as what it was - ride time - so the meter reads as though it
        // had never died, short only the distance. Too long a gap and
        // that stops being true, so it comes back paused instead.
        val resumable = wasRunning && gapMillis <= MAX_GAP_TO_RESUME_MILLIS
        val restoredStatus = if (resumable) RideStatus.RUNNING else RideStatus.PAUSED

        // A paused ride's gap is paused time, which is not ride time and
        // was never billed - so there is nothing to add for it.
        if (resumable) {
            priorSeconds += gapMillis / 1000L
        }

        _ride.value = Ride(
            id = record.id,
            name = record.name,
            status = restoredStatus,
            startTime = record.startTime,
            // The last moment the ride is known to have been running,
            // which is where its final persist left it - not now, and
            // not the row's endTime, which for a live row is only a
            // stand-in for the start time.
            endTime = if (resumable) null else lastKnownLive,
            startLocation = record.startLocation,
            endLocation = record.endLocation,
            startPlaceId = record.startPlaceId,
            startPlaceName = startPlaceName,
            endPlaceId = record.endPlaceId,
            endPlaceName = endPlaceName,
            meters = record.meters,
            elapsedSeconds = priorSeconds,
            manualAmount = record.manualAmount,
            stopPlaceIds = stopPlaceIds,
            stopPlaceNames = stopPlaceNames,
        )

        recordedStops.clear()
        openVisit = null
        openVisitPlace = null
        recordedStops.addAll(stops.sortedBy { it.sequence })

        // From the rows rather than from record.stoppedSeconds: the
        // column is a snapshot taken every five seconds, the rows are
        // what actually happened.
        _ride.value = _ride.value.copy(stoppedSeconds = stoppedSecondsNow())

        // A name still ending in "->" is one this class wrote and never
        // finished, so save() may still complete it with the end place.
        // Anything else is the user's and stays theirs.
        nameIsAutomatic = record.name.trimEnd().endsWith("->")

        // The dwell that was under way is not recoverable - the anchor
        // lived in the dead process - so stop detection starts afresh
        // from the next fix.
        dwellAnchor = null
        dwellAnchorTime = 0L
        dwellStop = null
        dwellPausedAt = 0L

        // A resumed ride needs a provider to resume into; nothing else
        // creates one, since start() is what normally does.
        distanceProvider = createDistanceProvider(activeSettings)

        // A ride coming back RUNNING has to be metering again, not just
        // labelled as running.
        if (resumable) {
            liveRideStore.markStatus(RideStatus.RUNNING)
            startTimeTracking()
            startDistanceTracking()
            startPersisting()
        } else {
            liveRideStore.markStatus(RideStatus.PAUSED)
        }

        Log.d("RideMeter", "Restored ride ${record.id} as $restoredStatus after ${gapMillis}ms")

        return Restored(
            status = restoredStatus,
            gapMillis = gapMillis,
            wasRunning = wasRunning,
        )
    }

    /**
     * What restore() made of an interrupted ride, for the service to
     * tell the user about.
     *
     * [wasRunning] with a [status] of PAUSED is the case worth
     * announcing loudest: the ride was metering, the gap was too long to
     * assume it still is, and the user has to decide.
     */
    data class Restored(
        val status: RideStatus,
        val gapMillis: Long,
        val wasRunning: Boolean,
    )

    /**
     * Stops tracking and provisionally ends the ride here: wherever we
     * are right now becomes the end location and the current moment
     * becomes the end time, so a paused ride is a complete, saveable
     * ride rather than a half-written one.
     *
     * Deliberately does NOT resolve the end location to a Place.
     * PlaceResolver creates a row when nothing matches, and pausing is
     * not a commitment to end here - the ride may well resume. The
     * displayed end place comes from the last detected stop until the
     * user actually confirms the save (see save()).
     */
    fun pause() {

        stopTracking()

        val pausedAt = System.currentTimeMillis()

        _ride.value = _ride.value.copy(
            status = RideStatus.PAUSED,
            endLocation = gpsInfo.value.rideLocation ?: _ride.value.endLocation,
            endTime = pausedAt,
            stoppedSeconds = stoppedSecondsNow(pausedAt),
        )

        dwellPausedAt = pausedAt

        persistNow()
        liveRideStore.markStatus(RideStatus.PAUSED)

        sounds.play(RideSounds.Sound.PAUSE)
    }

    /**
     * Takes back the provisional ending recorded by pause() - the ride
     * hasn't ended after all - and picks tracking back up. Distance and
     * elapsed time continue from where they were, because pause() never
     * reset the providers.
     *
     * [departedAtMillis] is for the automatic path only, and carries the
     * same lag start() does: a resume triggered by leaving a place
     * happens when the crossing is *confirmed*, by which time the vehicle
     * has been driving for up to a minute and is a few hundred metres
     * away. Given it, the ride picks up as of the departure rather than
     * as of now, with [metersAlready] - the distance from where the ride
     * paused to where the confirming fix found it - already on the
     * odometer. See rebaseForDeparture.
     *
     * A resume by hand passes neither and behaves exactly as it always
     * has: the user is pressing the button where the vehicle is, so
     * there is no gap to account for.
     */
    fun resume(departedAtMillis: Long? = null, metersAlready: Double = 0.0) {

        // The dwell has been sitting still along with the ride. Shifting
        // its anchor forward by the length of the pause leaves the time
        // it had accrued before the pause intact and discounts the pause
        // itself - for the stopped-time total and for the stop-detection
        // threshold alike, both of which should measure a ride's time and
        // not the wall clock. See dwellPausedAt.
        if (dwellPausedAt != 0L) {
            if (dwellAnchor != null) {
                dwellAnchorTime += System.currentTimeMillis() - dwellPausedAt
            }
            dwellPausedAt = 0L
        }

        if (departedAtMillis != null) {
            rebaseForDeparture(departedAtMillis, metersAlready)
        }

        _ride.value = _ride.value.copy(
            status = RideStatus.RUNNING,
            endTime = null,
        )

        persistNow()
        liveRideStore.markStatus(RideStatus.RUNNING)

        startTimeTracking()
        startDistanceTracking()
        startPersisting()

        sounds.play(RideSounds.Sound.RESUME)
    }

    /**
     * Moves a paused ride's counters forward to account for the drive it
     * has already done since it was left - the gap between the vehicle
     * pulling out and the departure being confirmed.
     *
     * Time and distance are handled differently, and not for symmetry's
     * sake:
     *
     * Time is simply added to. The gap between the pause and the
     * departure is a parked vehicle and is not billable - that is the
     * whole point of pausing - so only what has been driven *since the
     * departure* is owed. The time provider accumulates across a pause
     * on its own and is left alone to carry on.
     *
     * Distance cannot be added the same way, because the distance
     * provider still remembers where it was when the ride paused. Its
     * first fix after resuming measures from there, which is the very
     * gap being added here - counted twice - except when the gap happens
     * to clear the 100 m the provider discards as drift, which is
     * precisely the case where it would be right. So its running total
     * is folded into priorMeters, the gap goes on top of that, and the
     * provider is reset to count the rest from zero with no memory of
     * where the ride was parked.
     */
    // Note (2026-09-03): GpsDistanceProvider.start() now clears its own
    // anchor, so the "counted twice" worry below no longer depends on a
    // 100 m cap catching it. The reset here is still right - it is what
    // makes metersAlready the whole of the gap - but nothing rests on a
    // filter written for drift any more.
    private fun rebaseForDeparture(departedAtMillis: Long, metersAlready: Double) {

        val now = System.currentTimeMillis()
        val departedAt = departedAtMillis.coerceIn(now - MAX_BACKDATE_MILLIS, now)
        val drivenSeconds = (now - departedAt) / 1000L

        priorSeconds += drivenSeconds
        priorMeters = _ride.value.meters + metersAlready.coerceAtLeast(0.0)

        distanceProvider?.reset()

        Log.d(
            "RideMeter",
            "Resuming as of ${drivenSeconds}s ago, " +
                "${metersAlready.toInt()}m already driven"
        )

        // Applied to the ride here rather than left to the collectors:
        // the ride screen is live and would otherwise show the paused
        // figures until the next fix.
        _ride.value = _ride.value.copy(
            meters = priorMeters,
            elapsedSeconds = _ride.value.elapsedSeconds + drivenSeconds,
        )
    }

    /**
     * Ends the ride for good: this is the one and only commit point, and
     * the UI confirms with the user before calling it.
     *
     * Everything else about the ride is already in the database by the
     * time we get here - the row since start(), the stops since each was
     * detected. What is left is genuinely only what could not be decided
     * earlier: where the ride actually ended.
     *
     * Uses activeSettings - the settings captured back at start() - not
     * whatever is current now. The rates a ride was metered at belong to
     * that ride; re-reading live settings here would rewrite the row's
     * rates, and its calculated amount, with rates that were never in
     * effect during it.
     */
    suspend fun save() {

        Log.d("RideMeter", "Saving ride: ${_ride.value}")

        stopTracking()

        if (gpsInfo.value.rideLocation != null) {
            _ride.value = _ride.value.copy(endLocation = gpsInfo.value.rideLocation)
        }

        // Every place whose set of linked points this save changes, so
        // each one is recomputed exactly once at the end - after the rows
        // are settled, since the recalculator averages what it reads back
        // out of the database.
        val touchedPlaceIds = mutableSetOf<String>()
        _ride.value.startPlaceId?.let { touchedPlaceIds.add(it) }
        _ride.value.endPlaceId?.let { touchedPlaceIds.add(it) }

        // Now - and only now, with the user having confirmed the ride
        // ends here - is it worth resolving the end point to a Place.
        // resolvePlace() creates a row when nothing matches, so doing
        // this any earlier (on every fix, or on a pause that may yet
        // resume) would litter the places table with locations the user
        // merely drove through.
        val endPlace = _ride.value.endLocation?.let {
            placeResolver.resolvePlace(it.latitude, it.longitude)
        }

        // If the last recorded stop is at the same place the ride ended,
        // it isn't a stop along the way - it's just the ride arriving and
        // settling before the user hit Save. Its row was written back
        // when it was detected (correctly: at the time, the ride was
        // still going), so ending here is what retracts it.
        if (endPlace != null &&
            recordedStops.isNotEmpty() &&
            recordedStops.last().placeId == endPlace.id
        ) {
            val trailing = recordedStops.removeAt(recordedStops.lastIndex)
            stopDao.delete(trailing)

            // The visit's row is gone, so nothing may extend it.
            if (openVisit?.id == trailing.id) {
                openVisit = null
                openVisitPlace = null
            }
            trailing.placeId?.let { touchedPlaceIds.add(it) }

            _ride.value = _ride.value.copy(
                stopPlaceIds = _ride.value.stopPlaceIds.dropLast(1),
                stopPlaceNames = _ride.value.stopPlaceNames.dropLast(1),
            )
        }

        // Both halves of the amount are settled here: a retracted stop
        // has just left recordedStops, and a ride saved while still
        // RUNNING has never had the pause that banks the dwell.
        //
        // A paused ride's dwell is frozen at the pause - see
        // stoppedSecondsNow - so confirming the save minutes later adds
        // nothing that was not ride time.
        _ride.value = _ride.value.copy(stoppedSeconds = stoppedSecondsNow())

        // The breakdown, because the total on its own cannot be argued
        // with. A ride that reports more time stopped than it lasted has
        // happened, and reading it back from the row afterwards could
        // not say which half was wrong - the stop rows, or the dwell,
        // or the clock the dwell was measured against. This says so at
        // the moment it is decided.
        logStoppedBreakdown()

        _ride.value = _ride.value.copy(
            // A running ride ends now. A paused one already ended - at
            // the pause, or at the last moment a restored ride is known
            // to have been metering - and confirming the save later
            // doesn't move that. endTime is null only while RUNNING, so
            // it is the whole test. Without this, a ride saved an hour
            // after it was paused reported an end time an hour after its
            // own duration ran out.
            endTime = _ride.value.endTime ?: System.currentTimeMillis(),
            endPlaceId = endPlace?.id,
            endPlaceName = endPlace?.name,
        )
        endPlace?.let { touchedPlaceIds.add(it.id) }

        // A name the user never touched now gets the other end of the
        // ride, which is only known here. One the user cleared is
        // treated as never having been named at all.
        if (nameIsAutomatic || _ride.value.name.isBlank()) {
            _ride.value = _ride.value.copy(
                name = automaticName(includeEnd = true)
            )
        }

        val currentRide = _ride.value
        val amount = AmountCalculator.calculate(currentRide, activeSettings).totalAmount

        Log.d("RideMeter", "Saved ride: $currentRide with amount: $amount")

        rideRepository.updateRide(
            currentRide.toRecord(
                settings = activeSettings,
                amount = amount,
            )
        )

        for (placeId in touchedPlaceIds) {
            placeLocationRecalculator.recompute(placeId)
        }

        timeProvider.reset()
        distanceProvider?.reset()
        distanceProvider = null

        liveRideStore.clear()

        _gpsInfo.value = GpsInfo()
        dwellAnchor = null
        dwellAnchorTime = 0L
        dwellStop = null
        dwellPausedAt = 0L
        recordedStops.clear()
        openVisit = null
        openVisitPlace = null
        priorMeters = 0.0
        priorSeconds = 0L

        nameIsAutomatic = false
        _ride.value = Ride()

        sounds.play(RideSounds.Sound.SAVE)
    }

    /**
     * Removes the ride's row entirely (it was created back in start()) and
     * clears the live window. Works whether the ride is still running,
     * paused, or already finished.
     */
    fun cancel() {

        Log.d("RideMeter", "Cancelling ride")

        val rideId = _ride.value.id

        // Places this ride contributed points to. Deleting the ride takes
        // those points away, so each of these has to be averaged again
        // without them - the stops were written as they happened, so
        // there is real data to walk back here, not just an in-memory
        // list to drop.
        val touchedPlaceIds = mutableSetOf<String>()
        _ride.value.startPlaceId?.let { touchedPlaceIds.add(it) }
        _ride.value.endPlaceId?.let { touchedPlaceIds.add(it) }
        recordedStops.forEach { stop -> stop.placeId?.let { touchedPlaceIds.add(it) } }

        stopTracking()

        timeProvider.reset()
        distanceProvider?.reset()
        distanceProvider = null

        liveRideStore.clear()

        _gpsInfo.value = GpsInfo()
        dwellAnchor = null
        dwellAnchorTime = 0L
        dwellStop = null
        dwellPausedAt = 0L
        recordedStops.clear()
        openVisit = null
        openVisitPlace = null
        priorMeters = 0.0
        priorSeconds = 0L

        if (rideId != Ride.INVALID_ID) {
            scope.launch {
                // The stops go with it - Stop.rideId is declared
                // ForeignKey.CASCADE - so the recompute below sees a
                // database with every trace of this ride already gone.
                rideRepository.deleteRide(rideId)

                for (placeId in touchedPlaceIds) {
                    placeLocationRecalculator.recompute(placeId)
                }
            }
        }

        nameIsAutomatic = false
        _ride.value = Ride()

        sounds.play(RideSounds.Sound.CANCEL)
    }

    private fun startTimeTracking() {

        val provider = timeProvider

        timeJob?.cancel()

        timeJob = scope.launch {

            provider.start()

            provider.elapsedSeconds.collect { seconds ->

                _ride.value = _ride.value.copy(
                    elapsedSeconds = priorSeconds + seconds,
                    stoppedSeconds = stoppedSecondsNow(),
                )
            }
        }
    }

    private fun startDistanceTracking() {

        val provider = distanceProvider ?: return

        distanceJob?.cancel()

        distanceJob = scope.launch {

            launch {

                provider.gpsInfo.collect {

                    _gpsInfo.value = it
                    // A ride started from a known place already has both
                    // its start location and its start place, so the
                    // first fix must not re-resolve and replace them.
                    if (
                        _ride.value.startLocation == null &&
                        gpsInfo.value.rideLocation != null
                    ) {
                        _ride.value =
                            _ride.value.copy(
                                startLocation = gpsInfo.value.rideLocation
                            )

                        resolveStartPlace()
                    }

                    handleStopDetection(it.rideLocation)
                }
            }

            provider.start()

            provider.distance.collect { meters ->

                _ride.value = _ride.value.copy(
                    meters = priorMeters + meters
                )
            }
        }
    }

    /**
     * Resolves the start place as soon as the first GPS fix comes in, so
     * "Start Place" can show up on screen right away instead of waiting
     * for finish().
     */
    private fun resolveStartPlace() {

        val location = _ride.value.startLocation ?: return

        startPlaceJob?.cancel()
        startPlaceJob = scope.launch {
            val place = placeResolver.resolvePlace(location.latitude, location.longitude)
            _ride.value = _ride.value.copy(
                startPlaceId = place.id,
                startPlaceName = place.name,
            )

            // The automatic name was made before there was a place to put
            // in it - this is that place arriving.
            if (nameIsAutomatic) {
                _ride.value = _ride.value.copy(
                    name = automaticName(includeEnd = false)
                )
            }
        }
    }

    private fun startPersisting() {

        persistJob?.cancel()

        persistJob = scope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL)
                persistNow()
            }
        }
    }

    private fun persistNow() {
        scope.launch { persistRideNow() }
    }

    /**
     * Suspending form of persistNow(), for callers that need the row
     * actually on disk before doing something that reads it back -
     * recomputing a place's average, above all.
     */
    private suspend fun persistRideNow() {

        val currentRide = _ride.value
        if (currentRide.id == Ride.INVALID_ID) return

        val amount = AmountCalculator.calculate(currentRide, activeSettings).totalAmount

        rideRepository.updateRide(
            currentRide.toRecord(
                settings = activeSettings,
                amount = amount,
            )
        )
    }

    private fun stopTracking() {

        timeJob?.cancel()
        distanceJob?.cancel()
        persistJob?.cancel()

        timeProvider.stop()
        distanceProvider?.stop()
    }

    /**
     * See the field comments above dwellAnchor for the algorithm. Runs on
     * every GPS fix; most calls just return immediately (still within
     * threshold of the current anchor, or first fix of the ride).
     */
    /**
     * How many of the ride's seconds have been spent stopped.
     *
     * Recomputed from scratch each call rather than accumulated, for the
     * same reason PlaceLocationRecalculator recomputes an average: the
     * inputs can change underneath it. A stop's end time is corrected
     * when the vehicle moves off, and save() can retract a trailing stop
     * entirely, so a running total would be a total of things that are no
     * longer true.
     *
     * The dwell under way is counted from the anchor rather than from its
     * row, because a stop the user recorded by hand holds a provisional
     * end time - the moment the button was pressed - until the departure
     * gives it a real one. Reading the row would stop that clock early.
     *
     * A dwell only counts once it has lasted long enough to become a
     * stop, or as soon as the user says it is one. Anything shorter is
     * a traffic light, and traffic lights are driving.
     */
    /**
     * Writes out how stopped time was arrived at, for the event log.
     *
     * Deliberately every component: a total that disagrees with the
     * ride's own duration is a symptom, and the components are what say
     * which part produced it.
     */
    private fun logStoppedBreakdown() {

        val now = System.currentTimeMillis()
        val frozenAt = if (dwellPausedAt != 0L) dwellPausedAt else now

        val live = dwellStop
        val threshold = activeSettings.stopDetectionMinutes * 60_000L

        // The same filter stoppedSecondsNow applies: a dwell already
        // written down as a stop is not counted twice.
        val finished = recordedStops
            .filter { live == null || it.id != live.id }
            .sumOf { it.endTime - it.startTime } / 1000

        val dwellMillis =
            if (dwellAnchor == null) 0L else frozenAt - dwellAnchorTime

        val dwellCounts = dwellAnchor != null && (live != null || dwellMillis >= threshold)

        // The parts have to add up to the total, or the line cannot be
        // checked - which is the only reason it exists. So report the
        // dwell as counted, and say when it was there but ignored.
        val dwellNote =
            when {
                dwellAnchor == null -> "no dwell"
                dwellCounts -> "dwell ${dwellMillis / 1000}s"
                else ->
                    "dwell ${dwellMillis / 1000}s NOT counted " +
                        "(under the ${activeSettings.stopDetectionMinutes}min threshold)"
            }

        EventLog.log(
            "RideMeter",
            "Stopped time: ${_ride.value.stoppedSeconds}s = " +
                "${recordedStops.size} stop(s) totalling ${finished}s + " +
                dwellNote +
                ", measured to ${if (dwellPausedAt != 0L) "the pause" else "now"}. " +
                "Ride elapsed ${_ride.value.elapsedSeconds}s"
        )
    }

    private fun stoppedSecondsNow(now: Long = System.currentTimeMillis()): Long {

        // While the ride is paused the dwell clock is frozen at the
        // moment it paused. Paused time is not ride time - the time
        // provider stops, so elapsedSeconds excludes it - and measuring
        // the dwell to the present would count it as stopped time the
        // ride never had.
        //
        // That is not hypothetical: an automatic save pauses on arrival
        // and commits autoSaveGraceMinutes later, so it reached this
        // with the clock running every single time. A seven minute ride
        // reported nine minutes stopped, the difference being exactly
        // the grace window. It billed as well as displayed wrong -
        // AmountCalculator clamps stopped to elapsed, so an overrun
        // makes moving time zero and charges the whole ride at
        // stoppedHourlyRate.
        //
        // Frozen here rather than at each caller: pause(), save(),
        // addStop() and the persist tick all ask this question, and
        // only pause() was passing the right clock.
        @Suppress("NAME_SHADOWING")
        val now = if (dwellPausedAt != 0L) minOf(now, dwellPausedAt) else now

        val live = dwellStop

        val finished = recordedStops
            .filter { live == null || it.id != live.id }
            .sumOf { it.endTime - it.startTime }

        val dwelling = if (dwellAnchor != null) {
            val elapsed = now - dwellAnchorTime
            val threshold = activeSettings.stopDetectionMinutes * 60_000L
            if (live != null || elapsed >= threshold) elapsed else 0L
        } else {
            0L
        }

        return (finished + dwelling) / 1000
    }

    private suspend fun handleStopDetection(location: RideLocation?) {
        if (location == null) return

        val now = System.currentTimeMillis()

        // Once the vehicle is outside the place it was visiting, the
        // visit is over: a later dwell there is a second visit and gets
        // its own stop. Without this, leaving Home Depot, driving across
        // town and coming back would extend the first stop across the
        // whole round trip.
        openVisitPlace?.let { visited ->
            val away = DistanceUtil.haversineMeters(
                visited.latitude, visited.longitude,
                location.latitude, location.longitude,
            )
            if (away > visited.radiusMeters) {
                openVisit = null
                openVisitPlace = null
            }
        }

        val anchor = dwellAnchor

        if (anchor == null) {
            dwellAnchor = location
            dwellAnchorTime = now
            return
        }

        val distance = DistanceUtil.haversineMeters(
            anchor.latitude, anchor.longitude,
            location.latitude, location.longitude
        )

        if (distance <= STOP_DISTANCE_THRESHOLD_METERS) {
            // Still within range of the anchor - dwell continues, anchor
            // itself doesn't move.
            return
        }

        // Moved beyond the anchor's radius - movement has resumed. Did we
        // dwell there long enough to count as a stop?
        val dwellMillis = now - dwellAnchorTime
        val thresholdMillis = activeSettings.stopDetectionMinutes * 60_000L

        val recorded = dwellStop

        if (recorded != null) {
            // This dwell already has a row - the user wrote it by hand
            // while we sat here. Detecting it again is what put the same
            // stop in the list twice, and it was easy to hit: press Add
            // Stop on arrival, sit longer than stopDetectionMinutes,
            // drive off. So this closes that row instead of writing a
            // second one, and the departure we just noticed is a truer
            // end time than the moment the button was pressed.
            extendStop(recorded, now)
            dwellStop = null

        } else if (dwellMillis >= thresholdMillis) {
            val place = placeResolver.resolvePlace(anchor.latitude, anchor.longitude)

            // If this is the very first stop and it's the same place the
            // ride started at, it isn't really a stop along the way -
            // just lingering (waiting on a GPS fix, chatting, etc.)
            // before actually leaving. Don't record it. A stop the user
            // adds by hand is exempt from this: that one was asked for.
            val isStartPlace =
                recordedStops.isEmpty() && place.id == _ride.value.startPlaceId

            if (!isStartPlace) {

                val open = openVisit

                if (open != null && open.placeId == place.id) {
                    // Same place, never left: one visit, so the stop it
                    // already has grows to cover this dwell as well. Its
                    // span then includes the minutes spent moving about
                    // inside - which is what the stops list has always
                    // displayed as the length of a visit, and what the
                    // billed stopped time now agrees with.
                    extendStop(open, now)

                    _ride.value = _ride.value.copy(
                        stoppedSeconds = stoppedSecondsNow(),
                    )
                    persistRideNow()
                } else {
                    recordStop(
                        location = anchor,
                        place = place,
                        startTime = dwellAnchorTime,
                        endTime = now,
                    )
                }
            }
        }

        dwellAnchor = location
        dwellAnchorTime = now
    }

    /**
     * Records a stop where the vehicle is right now because the user
     * said so, rather than waiting for the dwell to reach
     * stopDetectionMinutes and for movement to resume - the user
     * already knows this is a stop, and may well drive off before
     * detection would have agreed.
     *
     * It records the dwell already under way rather than inventing a
     * zero-length one: the anchor is where the vehicle has been sitting
     * and dwellAnchorTime is when it got there, which is exactly what
     * detection would have written had it been given the time. The
     * anchor is deliberately left where it is - the dwell hasn't ended,
     * it has only been written down early, and dwellStop is what stops
     * it being written a second time when it does end.
     *
     * Pressing again at the same place therefore doesn't add a second
     * stop; it extends the one already there, because that is the same
     * visit lasting longer.
     *
     * Works while PAUSED as well as RUNNING. The providers are stopped
     * then, but the anchor and the last fix both survive a pause, and
     * standing somewhere long enough to pause is exactly when a stop is
     * worth recording.
     *
     * Returns false when there is nothing to record: no ride yet, or no
     * fix has ever arrived, so there is no "here" to make a stop of.
     */
    suspend fun addStop(): Boolean {

        if (_ride.value.id == Ride.INVALID_ID) return false

        val now = System.currentTimeMillis()

        // No dwell under way - the ride is rolling, or the first fix has
        // only just landed. The stop starts here and now.
        if (dwellAnchor == null) {
            dwellAnchor = gpsInfo.value.rideLocation ?: return false
            dwellAnchorTime = now
        }

        val anchor = dwellAnchor ?: return false

        dwellStop?.let { recorded ->
            extendStop(recorded, now)
            return true
        }

        val place = placeResolver.resolvePlace(anchor.latitude, anchor.longitude)

        dwellStop = recordStop(
            location = anchor,
            place = place,
            startTime = dwellAnchorTime,
            endTime = now,
        )

        return true
    }

    /**
     * Gives a stop already in the database a later end time - the dwell
     * it describes turned out to last longer than it did when the row
     * was written.
     *
     * recordedStops mirrors those rows, so it is corrected too: save()
     * reads the trailing stop from it to decide whether the ride merely
     * arrived somewhere, and a stale copy there would answer that with
     * the wrong times.
     */
    private suspend fun extendStop(stop: Stop, endTime: Long): Stop {

        val extended = stop.copy(endTime = endTime)
        stopDao.update(extended)

        val index = recordedStops.indexOfFirst { it.id == stop.id }
        if (index >= 0) recordedStops[index] = extended

        dwellStop = if (dwellStop?.id == stop.id) extended else dwellStop
        if (openVisit?.id == stop.id) openVisit = extended

        return extended
    }

    /**
     * Writes one stop and everything that follows from it. Shared by
     * detection and by the user's own Add Stop, because a stop is the
     * same thing either way - only the decision to record it differs.
     */
    private suspend fun recordStop(
        location: RideLocation,
        place: Place,
        startTime: Long,
        endTime: Long,
    ): Stop {

        val stop = Stop(
            rideId = _ride.value.id,
            sequence = recordedStops.size,
            startTime = startTime,
            endTime = endTime,
            location = location,
            placeId = place.id,
        )

        stopDao.insert(stop)
        recordedStops.add(stop)

        openVisit = stop
        openVisitPlace = place

        // The most recent place we know the vehicle actually sat at is
        // the best answer available for "where does this ride end?"
        // until the user says otherwise. It costs nothing: this place
        // is already resolved and written, so adopting it creates no row
        // that recording the stop hadn't created anyway. save() replaces
        // it with a real resolve of wherever the ride is at that point.
        _ride.value = _ride.value.copy(
            stopPlaceIds = _ride.value.stopPlaceIds + place.id,
            stopPlaceNames = _ride.value.stopPlaceNames + place.name,
            endPlaceId = place.id,
            endPlaceName = place.name,
            endLocation = location,
            // The one-second tick refreshes this while RUNNING, but ADD
            // STOP works while PAUSED too, and then no tick is coming.
            stoppedSeconds = stoppedSecondsNow(),
        )

        // Write the ride's own row before recomputing: the recalculator
        // averages what it reads back out of the database, and it should
        // see this ride pointing at this place, not the previous one.
        persistRideNow()
        placeLocationRecalculator.recompute(place.id)

        return stop
    }

    fun close() {

        stopTracking()
        startPlaceJob?.cancel()

        scope.cancel()
    }
}
