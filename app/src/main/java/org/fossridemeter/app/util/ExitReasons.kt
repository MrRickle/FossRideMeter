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
package org.fossridemeter.app.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why the process died last time, written into the event log the next
 * time it starts.
 *
 * A ride that stops metering in the middle of a trip leaves the same
 * evidence whatever killed it: the row frozen at its last five-second
 * snapshot, the meter back at READY, and the watcher armed for a
 * departure from somewhere the vehicle isn't. What that evidence can't
 * say is whether the app crashed, was killed for memory, was frozen by
 * the OEM's battery management, or was stopped by the user - and those
 * want completely different fixes.
 *
 * Android keeps that answer in ApplicationExitInfo from API 30, and it
 * survives the death, which adb and logcat may not: the phone is in a
 * pocket when this happens and the ring buffer is long gone by the time
 * it's plugged in. So it goes in the same file as everything else that
 * happens when nobody is watching - see EventLog.
 */
object ExitReasons {

    private const val TAG = "ExitReasons"
    private const val PREFS = "exit_reasons"
    private const val KEY_LAST_LOGGED = "last_logged_timestamp"

    // The system keeps a short history per package; this is only ever
    // catching up on what happened since the last start, so a handful is
    // plenty.
    private const val MAX_RECORDS = 5

    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    /**
     * Logs every exit not already logged. Safe to call on every service
     * create: each record is written once, tracked by timestamp, so a
     * service that restarts twice doesn't report the same death twice.
     *
     * Silent below API 30, where the platform simply doesn't keep this.
     */
    fun logSinceLastStart(context: Context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // Nothing here is worth taking the app down for - this runs at
        // the top of onCreate, ahead of the tracking it is meant to be
        // explaining.
        try {
            logExits(context)
        } catch (e: Exception) {
            EventLog.log(TAG, "Could not read exit reasons: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    /**
     * The newest exit's reason in a few words, for telling the user why
     * the app vanished - "LOW MEMORY (system reclaimed it)" and the
     * like. Null before API 30, or when the system has kept no record.
     *
     * Deliberately the same wording the event log uses, so the
     * notification and the log agree.
     */
    fun mostRecentReason(context: Context): String? {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val manager = context.getSystemService(ActivityManager::class.java) ?: return null

        return runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .maxByOrNull { it.timestamp }
                ?.let { reasonName(it.reason) }
        }.getOrNull()
    }

    private fun logExits(context: Context) {

        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastLogged = prefs.getLong(KEY_LAST_LOGGED, 0L)

        val records = manager.getHistoricalProcessExitReasons(
            context.packageName,
            0,
            MAX_RECORDS
        )

        records
            .filter { it.timestamp > lastLogged }
            .sortedBy { it.timestamp }
            .forEach { EventLog.log(TAG, describe(it)) }

        records.maxOfOrNull { it.timestamp }?.let { newest ->
            prefs.edit().putLong(KEY_LAST_LOGGED, newest).apply()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(info: ApplicationExitInfo): String {

        val when_ = timestamp.format(Date(info.timestamp))
        val detail = info.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""

        return "Previous process ended $when_: ${reasonName(info.reason)}" +
                ", importance=${importanceName(info.importance)}" +
                ", rss=${info.rss / 1024}MB$detail"
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited on its own"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY (system reclaimed it)"
        ApplicationExitInfo.REASON_CRASH -> "CRASH (uncaught exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH (native)"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to initialize"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE RESOURCE USE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user asked (force stop / swipe)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped the package"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
        ApplicationExitInfo.REASON_FREEZER -> "FROZEN (cached process freezer)"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state changed"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package updated"
        ApplicationExitInfo.REASON_OTHER -> "other (see detail)"
        else -> "unknown ($reason)"
    }

    /**
     * What the system thought this process was worth at the moment it
     * took it. A location foreground service that dies at
     * FOREGROUND_SERVICE importance was not killed for being idle
     * housekeeping, which points at the OEM rather than at Android.
     */
    private fun importanceName(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "gone"
        else -> "importance $importance"
    }
}
