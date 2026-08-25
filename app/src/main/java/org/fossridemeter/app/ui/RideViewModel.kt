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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.fossridemeter.app.data.SettingsRepository
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.Ride
import org.fossridemeter.app.service.GpsInfo
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Intent
import android.os.IBinder
import org.fossridemeter.app.service.AutoState
import org.fossridemeter.app.service.PlaceWatcher
import org.fossridemeter.app.service.RideTrackingService
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.fossridemeter.app.model.RideStatus


class RideViewModel(application: Application) : AndroidViewModel(application) {

    private var isQuitting = false


    fun appBackgrounded() {
        Log.d("RideViewModel", "appBackgrounded, status=${ride.value.status}, isQuitting=$isQuitting")
        if (!isQuitting && ride.value.status != RideStatus.READY) {
            requireService().showBubble()
        }
    }

    fun appForegrounded() {
        Log.d("RideViewModel", "appForegrounded")
        if (serviceStarted) {
            requireService().hideBubble()
        }
    }



    fun cancel() {
        requireService().cancel()
    }

    private val settingsRepository =
        SettingsRepository(application)

    val settings: StateFlow<Settings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Settings()
        )

    private var rideService: RideTrackingService? = null
    private var serviceStarted = false

    private val serviceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {
                Log.d("RideViewModel", "Connected to RideTrackingService")
                val binder = service as RideTrackingService.LocalBinder
                rideService = binder.getService()

                viewModelScope.launch {
                    requireService().ride.collect { _ride.value = it }
                }
                viewModelScope.launch {
                    requireService().gpsInfo.collect { _gpsInfo.value = it }
                }
                viewModelScope.launch {
                    requireService().autoState.collect { _autoState.value = it }
                }
                viewModelScope.launch {
                    requireService().watchFix.collect { _watchFix.value = it }
                }

                requireService().test()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                rideService = null
            }
        }

    private val _ride = MutableStateFlow(Ride())
    val ride: StateFlow<Ride> get() = _ride

    private val _gpsInfo = MutableStateFlow(GpsInfo())
    val gpsInfo: StateFlow<GpsInfo> = _gpsInfo

    // What the automatic-by-location machinery is doing, and where it
    // thinks the vehicle is. Neither is visible from gpsInfo: that
    // reports the ride's own DistanceProvider, which doesn't exist while
    // READY - exactly when watching is the only thing running.
    private val _autoState = MutableStateFlow<AutoState>(AutoState.Off)
    val autoState: StateFlow<AutoState> = _autoState

    private val _watchFix = MutableStateFlow<PlaceWatcher.WatchFix?>(null)
    val watchFix: StateFlow<PlaceWatcher.WatchFix?> = _watchFix

    fun startServiceIfNeeded() {
        if (serviceStarted) return
        serviceStarted = true

        val app = getApplication<Application>()
        val intent = Intent(app, RideTrackingService::class.java)

        ContextCompat.startForegroundService(app, intent)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun updateName(name: String) {
        requireService().updateName(name)
    }

    fun updateManualAmount(amount: Double?) {
        requireService().updateManualAmount(amount)
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsRepository.update(settings)
        }
    }

    private fun requireService(): RideTrackingService =
        checkNotNull(rideService) {
            "RideTrackingService is not connected."
        }

    fun start() {
        requireService().start(settings.value)
    }

    fun pause() {
        requireService().pause()
    }

    fun resume() {
        requireService().resume()
    }

    /**
     * Ends the ride and clears the live window. The UI confirms with the
     * user first - this is the point of no return.
     *
     * Deliberately passes no settings: the ride keeps the rates it was
     * metered at, which the service still holds from start().
     */
    fun save() {
        viewModelScope.launch {
            requireService().save()
        }
    }

    /**
     * Records a stop where the vehicle is now, without ending anything.
     * Silently does nothing if no fix has arrived yet - the screen's GPS
     * line already says why, and the button is disabled until one does.
     */
    fun addStop() {
        viewModelScope.launch {
            requireService().addStop()
        }
    }

    fun quit() {
        isQuitting = true
        requireService().quit()
        getApplication<Application>().unbindService(serviceConnection)
        rideService = null
        serviceStarted = false
    }

    override fun onCleared() {
        if (serviceStarted) {
            getApplication<Application>().unbindService(serviceConnection)
        }
        rideService = null
        serviceStarted = false
    }
}
