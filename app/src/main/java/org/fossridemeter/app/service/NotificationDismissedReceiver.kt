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
import org.fossridemeter.app.util.EventLog

/**
 * Records the ongoing notification being dismissed by hand.
 *
 * The log line says "manually" rather than naming the user: what
 * arrives here is a dismissal, and the phone cannot tell whose thumb it
 * was - or whether it was a thumb at all rather than a system UI gesture
 * or an accessibility service. The log is a record of what happened.
 *
 * From Android 14 a foreground service's notification can be dismissed,
 * and the service goes on running - so nothing is wrong when it happens.
 * What is wrong is not being able to tell afterwards. While a ride is
 * running the notification comes straight back, because the ride state
 * changes every second and every change re-posts it; while the app is
 * only watching for a departure, nothing changes, so nothing re-posts
 * it and it stays gone. The two look identical from the outside: an app
 * that is quietly still watching, with no notification.
 *
 * This does not put it back. Android made the dismissal deliberate and
 * re-posting a minute later would take that away. It writes a line to
 * the event log instead, so the next person to wonder where the
 * notification went can read the answer - which is the same reason the
 * automatic-by-location events are logged at all: they happen with no
 * adb attached.
 *
 * A receiver rather than a service action, deliberately. A dismissal is
 * not a tap on a notification action and doesn't carry the same
 * permission to start a service from the background; a broadcast to our
 * own unexported component always arrives.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        EventLog.init(context)

        val state = intent.getStringExtra(EXTRA_STATE) ?: "unknown state"

        EventLog.log(
            TAG,
            "Notification dismissed manually while: $state. Still " +
                    "running - it will reappear at the next change of state."
        )
    }

    companion object {
        const val ACTION_DISMISSED = "org.fossridemeter.app.action.NOTIFICATION_DISMISSED"
        const val EXTRA_STATE = "state"
        private const val TAG = "RideTrackingService"
    }
}
