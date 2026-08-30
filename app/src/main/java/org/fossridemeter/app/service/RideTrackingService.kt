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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.fossridemeter.app.data.AppRepository
import org.fossridemeter.app.data.LiveRideStore
import org.fossridemeter.app.data.RideRepository
import org.fossridemeter.app.model.DistanceProviderType
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.model.Settings
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.withContext
import org.fossridemeter.app.MainActivity
import org.fossridemeter.app.util.displayAmount
import android.provider.Settings as AndroidProvider
import org.fossridemeter.app.data.SettingsRepository
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import org.fossridemeter.app.R
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.fossridemeter.app.data.PlaceDao
import org.fossridemeter.app.util.DistanceUtil
import org.fossridemeter.app.util.EventLog
import org.fossridemeter.app.util.ExitReasons

// ...
class RideTrackingService : Service() {

    private class ClickableLinearLayout(context: Context) : LinearLayout(context) {
        override fun performClick(): Boolean {
            // Optional: play the default click sound / accessibility feedback
            super.performClick()
            return true
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleTextView: TextView? = null

    private var bubbleParams: WindowManager.LayoutParams? = null

    fun showBubble() {
        if (bubbleView != null) return
        if (!AndroidProvider.canDrawOverlays(this)) return

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }
            bubbleParams = params

            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val backgroundColor = if (isDarkMode) 0xEE1C1C1C.toInt() else 0xEEFFFFFF.toInt()
            val textColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val borderColor = if (isDarkMode) 0xFF888888.toInt() else 0xFF444444.toInt()

            val bubbleBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(backgroundColor)
                setStroke(4, borderColor)
            }

            // Create children FRESH every time
            val icon = ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                    rightMargin = 12
                }
            }

            val amountText = TextView(this).apply {
                text = displayAmount(AmountCalculator.calculate(ride.value, currentSettings).totalAmount)
                setTextColor(textColor)
                textSize = 48f
            }

            val view = ClickableLinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 24, 32, 24)
                background = bubbleBackground
                addView(icon)          // ← these must be brand new instances
                addView(amountText)
            }

            // Touch handling
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false

            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(v, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            v.performClick()
                            val intent = Intent(this@RideTrackingService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            startActivity(intent)
                            hideBubble()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
            bubbleView = view
            bubbleTextView = amountText

        } catch (e: Exception) {
            Log.e("RideTrackingService", "Failed to show bubble", e)
        }
    }

    fun hideBubble() {
        bubbleView?.let { view ->
            try {
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                Log.e("RideTrackingService", "Error removing bubble", e)
            }
        }
        bubbleView = null
        bubbleTextView = null
        bubbleParams = null
    }

    fun cancel() {
        cancelGrace()
        tracker.cancel()
        reconcileWatching()
    }

    companion object {
        private const val CHANNEL_ID = "ride_tracking"
        private const val NOTIFICATION_ID = 1

        // The interruption alert, on its own id so it sits beside the
        // ongoing ride notification rather than replacing it.
        private const val ALERT_NOTIFICATION_ID = 2
        private const val ALERT_CHANNEL_ID = "ride_alerts"

        // Notification actions, so the grace window after an automatic
        // arrival can be answered without opening the app - which is the
        // point of it being automatic.
        const val ACTION_RESUME = "org.fossridemeter.app.action.RESUME"
        const val ACTION_SAVE_NOW = "org.fossridemeter.app.action.SAVE_NOW"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RideTrackingService =
            this@RideTrackingService
    }

    private lateinit var rideRepository: RideRepository
    private lateinit var liveRideStore: LiveRideStore
    private lateinit var tracker: RideMeter

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    val ride: StateFlow<Ride>
        get() = tracker.ride

    val gpsInfo: StateFlow<GpsInfo>
        get() = tracker.gpsInfo

     private lateinit var settingsRepository: SettingsRepository

    @Volatile
    private var currentSettings: Settings = Settings()

    private lateinit var placeDao: PlaceDao
    private lateinit var placeWatcher: PlaceWatcher

    private val _autoState = MutableStateFlow<AutoState>(AutoState.Off)
    val autoState: StateFlow<AutoState> = _autoState.asStateFlow()

    // The watcher's own last fix. Mirrored rather than exposed directly
    // because placeWatcher is lateinit and the UI binds before it exists.
    private val _watchFix = MutableStateFlow<PlaceWatcher.WatchFix?>(null)
    val watchFix: StateFlow<PlaceWatcher.WatchFix?> = _watchFix.asStateFlow()

    // Runs only between an automatic arrival and the save it will commit.
    private var graceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        EventLog.init(this)
        createNotificationChannel()

        // Every restart is worth a line, because a restart is not a
        // neutral event here: the meter comes back READY with no memory
        // of a ride that may still have been running, so a gap in this
        // log followed by this line is the signature of exactly that.
        // What killed the previous process is written directly after it.
        EventLog.log("RideTrackingService", "Service created")
        ExitReasons.logSinceLastStart(this)

        val notification = buildNotification(RideStatus.READY, AutoState.Off)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        rideRepository = AppRepository.get(this)
        liveRideStore = LiveRideStore(this)
        settingsRepository = SettingsRepository(this)
        placeDao = AppRepository.getPlaceDao(this)

        tracker = createTracker()

        placeWatcher = PlaceWatcher(
            context = this,
            placeDao = placeDao,
            scope = serviceScope,
        )

        serviceScope.launch { restoreInterruptedRide() }

        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                currentSettings = settings
                // Cadence and accuracy are read at start(), so a change
                // to either has to re-decide the watcher rather than
                // waiting for the ride status to happen to move.
                reconcileWatching()
            }
        }

        serviceScope.launch {
            tracker.ride.collect { ride ->
                refreshNotification()
                val amount = AmountCalculator.calculate(ride, currentSettings).totalAmount
                withContext(Dispatchers.Main) {
                    bubbleTextView?.text = displayAmount(amount)
                }
            }
        }

        // What the watcher should be looking for depends on the ride's
        // status, and which places qualify depends on their flags - so a
        // change to either has to re-decide it. Both are flows, so this
        // stays correct when the user flags a place mid-ride.
        serviceScope.launch {
            tracker.ride.collect { reconcileWatching() }
        }

        serviceScope.launch {
            placeDao.getAll().collect { places ->
                flaggedForStart = places.any { it.autoStart }
                flaggedForSave = places.any { it.autoSave }
                reconcileWatching()
            }
        }

        serviceScope.launch {
            placeWatcher.crossings.collect { crossing -> onCrossing(crossing) }
        }

        serviceScope.launch {
            placeWatcher.lastFix.collect { _watchFix.value = it }
        }

        // The other half of "arrivals cost no battery": PlaceWatcher does
        // not poll in ARRIVAL mode, so the fixes have to come from the
        // ride's own stream, which is already running at 1 Hz.
        serviceScope.launch {
            tracker.gpsInfo.collect { info ->
                val location = info.rideLocation
                if (location != null && _autoState.value == AutoState.WatchingArrival) {
                    placeWatcher.submit(location, info.accuracy)
                }
            }
        }

        serviceScope.launch {
            _autoState.collect { refreshNotification() }
        }
    }

    /**
     * Picks up a ride that was being metered when the previous process
     * died.
     *
     * This is not an exotic case. The system reclaims processes under
     * memory pressure with no warning and nothing wrong - a location
     * foreground service is a late choice, not an exempt one - and
     * START_STICKY brings the service straight back. What came back was a
     * fresh RideMeter at READY, so an hour-old ride simply stopped
     * existing: metering ended silently, the row stayed frozen at its
     * last five-second snapshot with no end place and no way to reach it,
     * and the watcher re-armed for a departure from somewhere the vehicle
     * wasn't. Everything needed to recover it was already in the
     * database; nothing looked.
     *
     * The ride comes back PAUSED - see RideMeter.restore for why the gap
     * makes that the only honest status.
     */
    private suspend fun restoreInterruptedRide() {

        val rideId = liveRideStore.liveRideId() ?: return

        val record = AppRepository.getRideDao(this).getById(rideId)
        if (record == null) {
            // The row is gone - cancelled, or wiped by a destructive
            // migration - so the marker is stale.
            EventLog.log("RideTrackingService", "Interrupted ride $rideId no longer exists")
            liveRideStore.clear()
            return
        }

        val stopDao = AppRepository.getStopDao(this)
        val stops = stopDao.getByRideId(rideId)
        // Ids and names in one pass, so the two lists cannot end up
        // describing different stops: a stop whose place row has since
        // been deleted has to drop out of both or neither.
        val stopPlaces = stops.mapNotNull { stop ->
            stop.placeId?.let { id -> placeDao.getById(id)?.let { id to it.name } }
        }

        // What it was doing when the process died. A marker written by a
        // build that didn't record one reads as PAUSED, which is what
        // every interrupted ride used to come back as.
        val wasRunning = liveRideStore.liveRideStatus() == RideStatus.RUNNING

        val restored = tracker.restore(
            record = record,
            stops = stops,
            stopPlaceIds = stopPlaces.map { it.first },
            stopPlaceNames = stopPlaces.map { it.second },
            startPlaceName = record.startPlaceId?.let { placeDao.getById(it)?.name },
            endPlaceName = record.endPlaceId?.let { placeDao.getById(it)?.name },
            settings = settingsRepository.settings.first(),
            wasRunning = wasRunning,
        ) ?: return

        EventLog.log(
            "RideTrackingService",
            "Restored interrupted ride: started ${record.startTime}, " +
                "${record.meters}m over ${record.elapsedSeconds}s, " +
                "${stops.size} stop(s) - was ${if (wasRunning) "RUNNING" else "PAUSED"}, " +
                "gone ${restored.gapMillis / 1000}s, back as ${restored.status}"
        )

        announceInterruption(restored)

        reconcileWatching()
        refreshNotification()
    }

    private fun createTracker(): RideMeter =
        RideMeter(
            timeProvider = SystemTimeProvider(),
            createDistanceProvider = { settings ->
                when (settings.distanceProvider) {
                    DistanceProviderType.SIMULATOR -> SimulatedDistanceProvider()
                    DistanceProviderType.GPS -> GpsDistanceProvider(this, settings)
                }
            },
            rideRepository = rideRepository,
            placeResolver = AppRepository.getPlaceResolver(this),
            placeLocationRecalculator = AppRepository.getPlaceLocationRecalculator(this),
            stopDao = AppRepository.getStopDao(this),
            liveRideStore = liveRideStore,
            // currentSettings is a var the settings collector keeps
            // fresh, so this reads whatever is set now rather than
            // whatever was set when the service started.
            sounds = RideSounds(this, serviceScope) { currentSettings },
        )

    /**
     * Points the watcher at whichever crossing is meaningful for the
     * ride's current status, or shuts it off when none is:
     *
     * * READY - a departure from an autoStart place would begin a ride.
     * * RUNNING - an arrival at an autoSave place would end one.
     * * PAUSED - a departure from an autoStart place would *resume* the
     *   paused ride. Same crossing as READY, different meaning: see
     *   onDeparted.
     *
     * Synchronized because it is called from five different collectors -
     * settings, the ride, the places table, and the auto paths - all on
     * Dispatchers.Default. Deciding the desired state and acting on it is
     * a check-then-act, and two of them racing did both halves twice:
     * the log shows a single instant arming the watcher as
     * WatchingDeparture *and* WatchingResume, and each restart
     * registering its own alarm receiver while the other was still
     * registered. See onSweepDue for what a receiver left behind does.
     *
     * Leaves a countdown alone: PendingSave is a live grace window, not
     * a watching state, and only resume/save/cancel end it. That is also
     * what keeps auto-resume out of the grace window - driving off during
     * a countdown is answered by the notification's Resume button, not by
     * a second automatic decision layered on the first.
     */
    @Synchronized
    private fun reconcileWatching() {

        if (_autoState.value is AutoState.PendingSave) return

        if (!::placeWatcher.isInitialized) return

        val status = tracker.ride.value.status

        val mode = when (status) {
            RideStatus.READY -> PlaceWatcher.Mode.DEPARTURE
            RideStatus.RUNNING -> PlaceWatcher.Mode.ARRIVAL
            RideStatus.PAUSED -> PlaceWatcher.Mode.DEPARTURE
        }

        if (!hasFlagFor(mode)) {
            // Stood down and switched off look the same from here - the
            // watcher stops either way - but they are not the same thing
            // to the user. Paused with an autoSave place still flagged
            // means "nothing until you resume by hand"; Off means there
            // is nothing to watch for at all.
            val stoodDown =
                if (status == RideStatus.PAUSED && flaggedForSave) {
                    AutoState.Suspended
                } else {
                    AutoState.Off
                }
            if (_autoState.value != stoodDown) {
                EventLog.log(
                    "RideTrackingService",
                    "Auto state -> $stoodDown (status=$status, " +
                        "autoStart places=$flaggedForStart, autoSave places=$flaggedForSave)"
                )
            }
            placeWatcher.stop()
            watchingWith = null
            _autoState.value = stoodDown
            return
        }

        val desired = when (mode) {
            PlaceWatcher.Mode.DEPARTURE ->
                if (status == RideStatus.PAUSED) {
                    AutoState.WatchingResume
                } else {
                    AutoState.WatchingDeparture
                }
            PlaceWatcher.Mode.ARRIVAL -> AutoState.WatchingArrival
        }

        // start() resets the watcher's idea of which side of the boundary
        // it's on, so only call it when something it depends on changed.
        val watchSettingsChanged =
            watchingWith?.autoWatchSeconds != currentSettings.autoWatchSeconds ||
                watchingWith?.autoWatchAccuracy != currentSettings.autoWatchAccuracy

        if (_autoState.value != desired || watchSettingsChanged) {
            EventLog.log(
                "RideTrackingService",
                "Auto state -> $desired (every ${currentSettings.autoWatchSeconds}s, " +
                    "${currentSettings.autoWatchAccuracy})"
            )
            placeWatcher.start(
                mode = mode,
                watchSeconds = currentSettings.autoWatchSeconds,
                accuracy = currentSettings.autoWatchAccuracy,
            )
            watchingWith = currentSettings
            _autoState.value = desired
        }
    }

    private var flaggedForStart = false
    private var flaggedForSave = false

    // The settings the watcher was last started with, so a change to the
    // cadence or the accuracy takes effect without waiting for the ride
    // status to happen to change.
    private var watchingWith: Settings? = null

    private fun hasFlagFor(mode: PlaceWatcher.Mode): Boolean =
        when (mode) {
            PlaceWatcher.Mode.DEPARTURE -> flaggedForStart
            PlaceWatcher.Mode.ARRIVAL -> flaggedForSave
        }

    private fun onCrossing(crossing: PlaceWatcher.Crossing) {
        when (crossing) {
            is PlaceWatcher.Crossing.Departed -> onDeparted(crossing)
            is PlaceWatcher.Crossing.Arrived -> onArrived(crossing.place.name)
        }
    }

    /**
     * Left a place flagged autoStart. What that means depends on whether
     * a ride is already under way:
     *
     * * READY - begin metering. Uses live settings, exactly as pressing
     *   START does; this is that press. The place is handed to the meter
     *   rather than left to be discovered, because a confirmed departure
     *   means the vehicle has already left, so the ride's first fix is
     *   down the road and resolving it would invent a new place beside
     *   the one actually departed from. See RideMeter.start.
     *
     *   The when and the how far go with it, for the same reason. The
     *   crossing is reported after it happened - a poll behind at best,
     *   a minute or more when confirmation has to wait - so the ride is
     *   dated from when the vehicle was first seen outside, and seeded
     *   with the distance from the place it left to where the confirming
     *   fix found it. Otherwise the ride starts at the place but meters
     *   from down the road, and the first leg of every automatic ride
     *   goes unbilled.
     * * PAUSED - resume the ride that is already there. Driving off from
     *   a pause is carrying on, not starting again, and starting again
     *   would strand the paused ride and split one job in two. The place
     *   is not handed over - the ride already has its start place - but
     *   the when and the how far are, for the same reason as above: the
     *   ride resumes as of the departure, with the drive from where it
     *   paused to where the crossing was confirmed already on it.
     * * RUNNING - nothing. Departures are not what ends a ride.
     */
    private fun onDeparted(crossing: PlaceWatcher.Crossing.Departed) {
        val place = crossing.place
        when (tracker.ride.value.status) {
            RideStatus.READY -> {
                val metersAlready = DistanceUtil.haversineMeters(
                    place.latitude, place.longitude,
                    crossing.confirmedAt.latitude, crossing.confirmedAt.longitude,
                )
                EventLog.log(
                    "RideTrackingService",
                    "Auto-start: departed ${place.name}, " +
                        "left ${(System.currentTimeMillis() - crossing.leftAtMillis) / 1000}s ago, " +
                        "${metersAlready.toInt()}m out"
                )
                tracker.start(
                    settings = currentSettings,
                    startPlace = place,
                    startedAtMillis = crossing.leftAtMillis,
                    metersAlready = metersAlready,
                )
            }
            RideStatus.PAUSED -> {
                // Measured from where the ride actually paused, not from
                // the place: pause() recorded that point and the ride was
                // metered right up to it, so it is the last position the
                // odometer knows. The place's own coordinates are the
                // fallback for a ride paused before it ever got a fix.
                val pausedAt = tracker.ride.value.endLocation
                val metersAlready = DistanceUtil.haversineMeters(
                    pausedAt?.latitude ?: place.latitude,
                    pausedAt?.longitude ?: place.longitude,
                    crossing.confirmedAt.latitude, crossing.confirmedAt.longitude,
                )
                EventLog.log(
                    "RideTrackingService",
                    "Auto-resume: departed ${place.name}, " +
                        "left ${(System.currentTimeMillis() - crossing.leftAtMillis) / 1000}s ago, " +
                        "${metersAlready.toInt()}m out"
                )
                resume(
                    departedAtMillis = crossing.leftAtMillis,
                    metersAlready = metersAlready,
                )
            }
            RideStatus.RUNNING -> return
        }
    }

    /**
     * Reached a place flagged autoSave. Pauses rather than saving: pause()
     * already writes a complete, saveable ride (end location and end time
     * included), so nothing is lost by waiting, and an arrival that turns
     * out to be a stop along the way can still be taken back by resuming.
     *
     * The save that follows is the only one in the app the user hasn't
     * explicitly confirmed, which is what the grace window is buying -
     * see "Automatic save" in decisions.md.
     */
    private fun onArrived(placeName: String) {
        if (tracker.ride.value.status != RideStatus.RUNNING) return

        EventLog.log("RideTrackingService", "Auto-save: arrived $placeName, pausing")

        tracker.pause()
        placeWatcher.stop()

        val graceSeconds = (currentSettings.autoSaveGraceMinutes * 60).coerceAtLeast(1)

        graceJob?.cancel()
        graceJob = serviceScope.launch {
            var remaining = graceSeconds
            while (isActive && remaining > 0) {
                _autoState.value = AutoState.PendingSave(placeName, remaining)
                delay(1000)
                remaining--
            }
            if (isActive) {
                EventLog.log("RideTrackingService", "Grace window elapsed, saving")

                // Drop the handle to this job *before* saving, and call
                // the meter directly rather than through save(): save()
                // calls cancelGrace(), which would cancel the very
                // coroutine running it and abandon the write half-done.
                graceJob = null
                _autoState.value = AutoState.Off

                tracker.save()
                reconcileWatching()
            }
        }
    }

    /**
     * Ends a countdown without saving. Called by every path that decides
     * the ride's fate itself - resume, save, cancel, start.
     */
    private fun cancelGrace() {
        graceJob?.cancel()
        graceJob = null
        if (_autoState.value is AutoState.PendingSave) {
            _autoState.value = AutoState.Off
            // The ride is still PAUSED at this point and places may still
            // be flagged, so Off is rarely the honest end state -
            // reconcile picks between stood down and re-armed.
            reconcileWatching()
        }
    }

    override fun onDestroy() {
        graceJob?.cancel()
        if (::placeWatcher.isInitialized) placeWatcher.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun statusText(status: RideStatus): String = when (status) {
        RideStatus.READY -> "Ready"
        RideStatus.RUNNING -> "Running"
        RideStatus.PAUSED -> "Paused"
    }

    /**
     * The one line of text that says what the app is doing. This
     * notification is the whole reason the status bar shows an icon while
     * watching, so it has to be honest about the difference between
     * metering a ride and merely waiting for one.
     */
    private fun notificationText(status: RideStatus, auto: AutoState): String =
        when (auto) {
            is AutoState.PendingSave ->
                "Arrived at ${auto.placeName} - saving in ${formatCountdown(auto.secondsRemaining)}"
            AutoState.WatchingDeparture -> "Watching for departure"
            AutoState.WatchingArrival -> "Running - watching for arrival"
            AutoState.WatchingResume -> "Paused - will resume when you drive off"
            AutoState.Suspended -> "Paused - nothing automatic until you resume"
            AutoState.Off -> statusText(status)
        }

    private fun formatCountdown(seconds: Int): String =
        "%d:%02d".format(seconds / 60, seconds % 60)

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(tracker.ride.value.status, _autoState.value)
        )
    }

    private fun buildNotification(status: RideStatus, auto: AutoState): Notification {

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FossRideMeter")
            .setContentText(notificationText(status, auto))
            .setContentIntent(openApp)
            .setOngoing(true)
            // setOngoing stopped meaning "cannot be dismissed" in Android
            // 14. This is what hears the swipe - see
            // NotificationDismissedReceiver, which logs it and leaves it
            // dismissed.
            .setDeleteIntent(dismissedIntent(notificationText(status, auto)))

        // Only the countdown gets buttons. It's the one moment the app is
        // about to do something irreversible on its own, so both answers
        // to it have to be reachable without opening the app.
        if (auto is AutoState.PendingSave) {
            builder.addAction(
                0,
                "Resume",
                serviceAction(ACTION_RESUME, requestCode = 1),
            )
            builder.addAction(
                0,
                "Save now",
                serviceAction(ACTION_SAVE_NOW, requestCode = 2),
            )
        }

        return builder.build()
    }

    /**
     * Fired when the user swipes the notification away, carrying what
     * it said at the time so the log line names the state that went
     * quiet. FLAG_UPDATE_CURRENT so the text is the current one rather
     * than whatever the first build put there.
     */
    private fun dismissedIntent(state: String): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, NotificationDismissedReceiver::class.java)
                .setAction(NotificationDismissedReceiver.ACTION_DISMISSED)
                .putExtra(NotificationDismissedReceiver.EXTRA_STATE, state),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RideTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * START_STICKY because watching is meant to outlive whatever the
     * system does to the process: if this is killed under memory
     * pressure, it should come back and carry on watching rather than
     * silently stop until the user next opens the app.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_RESUME -> {
                EventLog.log("RideTrackingService", "Resume from notification")
                resume()
            }
            ACTION_SAVE_NOW -> {
                EventLog.log("RideTrackingService", "Save from notification")
                cancelGrace()
                serviceScope.launch {
                    save()
                    reconcileWatching()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("RideTrackingService", "Service bound")
        return binder
    }

    private fun createNotificationChannel() {

        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        // Deliberately loud, and a separate channel so it can be:
        // the ongoing notification is IMPORTANCE_LOW because it is
        // there for the whole ride and must not nag, while this one
        // fires once and reports that the app was killed out from under
        // a ride. The user was not told at the time - nobody is - so
        // being told now, with sound, is the whole point.
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Ride interrupted",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Fires when the app was stopped by the system during a ride."
                enableVibration(true)
            }
        )
    }

    /**
     * Says out loud that the app was killed during a ride, how long it
     * was gone, and what the ride is doing now.
     *
     * The process dying mid-ride is silent by nature: the notification
     * goes with it, the bubble goes with it, and the phone in a pocket
     * shows nothing. Coming back quietly is how a ride ends up short by
     * however long nobody noticed.
     */
    private fun announceInterruption(restored: RideMeter.Restored) {

        val awayFor = formatGap(restored.gapMillis)
        val reason = ExitReasons.mostRecentReason(this)

        val what = when {
            restored.status == RideStatus.RUNNING ->
                "Metering again. The $awayFor it was gone counts as ride time; " +
                    "the distance driven in it could not be measured."

            restored.wasRunning ->
                "It was running, but $awayFor is too long to assume it still is. " +
                    "The ride is paused - resume it or save it."

            else ->
                "The ride was paused and still is."
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FossRideMeter was stopped after $awayFor")
            .setContentText(what)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOfNotNull(
                        reason?.let { "Stopped by: $it" },
                        "Away for: $awayFor",
                        what,
                    ).joinToString("\n")
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()

        // The noise comes from the channel: IMPORTANCE_HIGH with
        // DEFAULT_ALL is sound, vibration and a heads-up banner, and it
        // is the platform's way of being loud - so it still obeys Do Not
        // Disturb and whatever the user has set for this channel. The
        // ongoing ride notification is IMPORTANCE_LOW and silent, which
        // is why being killed mid-ride otherwise announces nothing.
        getSystemService(NotificationManager::class.java)
            .notify(ALERT_NOTIFICATION_ID, notification)
    }

    /** "4 min 12 s", "3 h 5 min" - short enough for a notification title. */
    private fun formatGap(millis: Long): String {

        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "$hours h ${minutes % 60} min"
            minutes > 0 -> "$minutes min ${seconds % 60} s"
            else -> "$seconds s"
        }
    }

    fun start(settings: Settings) {
        cancelGrace()
        tracker.start(settings)
    }

    fun pause() {
        cancelGrace()
        tracker.pause()
    }

    /**
     * Also the answer to the countdown's "Resume" button: taking the ride
     * back is exactly what that button means, so it cancels the pending
     * save and re-arms the watcher for the arrival that will follow.
     *
     * The two arguments are only ever passed by onDeparted - a resume by
     * hand has no lag to make up. See RideMeter.resume.
     */
    fun resume(departedAtMillis: Long? = null, metersAlready: Double = 0.0) {
        cancelGrace()
        tracker.resume(departedAtMillis, metersAlready)
        reconcileWatching()
    }

    /**
     * Ends the ride and clears the live window. Takes no settings - the
     * ride keeps the rates it was metered at (see RideMeter.save).
     */
    suspend fun save() {
        cancelGrace()
        tracker.save()
    }

    /**
     * Leaves the app. Whether that also stops the service depends on
     * whether anything still wants watching.
     *
     * Auto-start exists precisely for the mornings the user forgets to
     * open the app, so a quit that silently disarmed it would defeat the
     * feature - the habit of quitting at the end of a shift would leave
     * nothing running the next one. While any place is flagged, quitting
     * therefore closes the UI and drops the service to watch-only; the
     * notification says so. With no place flagged there is nothing to
     * watch for and it shuts down completely, as it always did.
     *
     * A ride that is RUNNING or PAUSED holds the service open for the
     * same reason, and this is not a nicety: close() cancels the meter's
     * scope, so stopping here took the ride with it. What was left behind
     * was the last five-second snapshot of a row nothing would ever
     * finish - metering stopped, the end time equal to the start time,
     * no end place, and no way back to it, because nothing restores an
     * in-progress ride at startup. Quitting is closing the UI; ending a
     * ride is save() or cancel(), and only the user gets to say which.
     *
     * Un-flagging every place is what fully stops it - there is
     * deliberately no separate master switch (see decisions.md).
     */
    fun quit() {
        Log.d("RideTrackingService", "Quit requested")
        hideBubble()
        cancelGrace()

        val rideIsLive = tracker.ride.value.status != RideStatus.READY

        if (flaggedForStart || flaggedForSave || rideIsLive) {
            reconcileWatching()
            refreshNotification()
            return
        }

        placeWatcher.close()
        tracker.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Records a stop at the current location on the user's say-so -
     * same rows, same place recompute, as a detected one. See
     * RideMeter.addStop.
     */
    suspend fun addStop(): Boolean = tracker.addStop()

    fun test() {
        Log.d("RideTrackingService", "ViewModel reached service")
    }

    fun updateName(name: String) = tracker.updateName(name)

    fun updateManualAmount(amount: Double?) = tracker.updateManualAmount(amount)
}
