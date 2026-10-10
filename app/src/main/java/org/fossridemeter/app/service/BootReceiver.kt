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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossridemeter.app.data.AppRepository
import org.fossridemeter.app.data.LiveRideStore
import org.fossridemeter.app.util.EventLog

/**
 * Brings watching back after a reboot, and after the app is updated.
 *
 * Without this the feature has a hole big enough to swallow it: auto-start
 * is for the mornings the user forgets the app exists, and a phone that
 * rebooted overnight would have forgotten too, so the first ride of the
 * day would go unmetered exactly when it was most relied on.
 *
 * An update is the same hole. Installing one kills the process, and
 * nothing brought the service back until the user next opened the app -
 * on 2026-10-10 the log simply stopped at the install. Android sends
 * MY_PACKAGE_REPLACED to the updated app alone, and it is on the same
 * short list as BOOT_COMPLETED of broadcasts allowed to start a
 * foreground service from the background.
 *
 * Starts nothing unless something asks for it: a flagged place, or a
 * ride that was being metered when the process went (LiveRideStore holds
 * its id outside the process, and the service restores it on create). A
 * user with neither gets no service and no notification.
 *
 * Note that `location` is permitted as a foreground service type from
 * BOOT_COMPLETED (unlike dataSync, camera, mediaPlayback, phoneCall,
 * mediaProjection and microphone). Being allowed to *start* is not the
 * same as being allowed to read location, though: a location service
 * started while the app is in the background gets no fixes at all
 * without ACCESS_BACKGROUND_LOCATION, which is why this feature needs
 * that permission and the in-app one does not.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val reason =
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) "App updated" else "Boot completed"

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val places = AppRepository.getPlaceDao(appContext).getAllOnce()
                val watching = places.any { it.autoStart || it.autoSave }
                val rideLive = LiveRideStore(appContext).liveRideId() != null
                val wanted = watching || rideLive

                EventLog.init(appContext)
                EventLog.log(
                    "BootReceiver",
                    "$reason, watching wanted=$watching, ride in progress=$rideLive"
                )

                if (wanted) {
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, RideTrackingService::class.java)
                    )
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to restart watching ($reason)", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
