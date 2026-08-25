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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An on-device log of the things that happen when nobody is watching.
 *
 * Automatic ride start is, by its nature, untestable over adb: it fires
 * while the phone is in a pocket and the car is pulling out of the drive,
 * which is exactly when it cannot be plugged into anything. logcat's ring
 * buffer might still hold the evidence on return, or might have been
 * flushed by every other app on the device - so the interesting events
 * are written to a file as well.
 *
 * Deliberately small and dumb: append a line, roll over at
 * [MAX_BYTES] keeping one previous file, never throw. A logger that can
 * break the thing it is observing is worse than no logger, so every
 * operation swallows its own failures.
 */
object EventLog {

    private const val FILE_NAME = "events.log"
    private const val PREVIOUS_FILE_NAME = "events.log.1"
    private const val MAX_BYTES = 256L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile
    private var appContext: Context? = null

    /**
     * Safe to call repeatedly. Anything logged before the first call is
     * dropped from the file (it still reaches logcat), which only affects
     * the moments before the service exists.
     */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun log(tag: String, message: String) {

        Log.d(tag, message)

        val context = appContext ?: return
        val line = "${timestamp.format(Date())} $tag: $message\n"

        scope.launch {
            try {
                mutex.withLock {
                    val file = File(context.filesDir, FILE_NAME)
                    if (file.length() > MAX_BYTES) {
                        val previous = File(context.filesDir, PREVIOUS_FILE_NAME)
                        previous.delete()
                        file.renameTo(previous)
                    }
                    file.appendText(line)
                }
            } catch (e: Exception) {
                Log.w("EventLog", "Could not write event log", e)
            }
        }
    }

    /** Oldest first, previous roll-over included. */
    fun read(context: Context): String =
        try {
            val previous = File(context.filesDir, PREVIOUS_FILE_NAME)
            val current = File(context.filesDir, FILE_NAME)
            buildString {
                if (previous.exists()) append(previous.readText())
                if (current.exists()) append(current.readText())
            }.ifBlank { "(no events logged yet)" }
        } catch (e: Exception) {
            "Could not read event log: ${e.message}"
        }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
            File(context.filesDir, PREVIOUS_FILE_NAME).delete()
        } catch (e: Exception) {
            Log.w("EventLog", "Could not clear event log", e)
        }
    }
}
