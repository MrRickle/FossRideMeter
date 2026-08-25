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
package org.fossridemeter.app.data

import android.content.Context
import org.fossridemeter.app.util.EventLog
import java.io.File
import java.io.IOException

/**
 * A restore that happens at the next process start, not at the tap.
 *
 * Swapping the database file underneath a running app doesn't work, and
 * the ways it fails are the ways that get shouted about: Room's instance
 * is closed but AppRepository still hands out DAOs built on it, every
 * Flow a screen is collecting belongs to the old handle, and the ride
 * being metered points at a row the new file has never heard of. What
 * the user sees is deletes that don't take and a list that has gone
 * empty - and no message at that moment can be trusted to be read.
 *
 * So [stage] only sets the file aside and marks it, and [apply] does the
 * swap on the next launch, before Room opens anything - the same order
 * SchemaRescue works in, and for the same reason. Whether the app
 * manages to restart itself afterwards stops being a correctness
 * question and becomes a convenience one: if the restart is refused, or
 * the process is killed first, or the user opens the app again next
 * week, the restore still lands exactly once, at the start of a process
 * where nothing has read the old file yet.
 *
 * The restored file is then whatever version wrote it, so it goes on to
 * meet the migrations, or SchemaRescue, exactly as an upgrade would.
 */
object PendingRestore {

    private const val TAG = "PendingRestore"
    private const val PENDING_NAME = "pending-restore.db"

    private fun pendingFile(context: Context) = File(context.filesDir, PENDING_NAME)

    /**
     * Copies [source] somewhere the next launch will find it. A copy
     * rather than a marker pointing at the original: the backup lives in
     * external files, which the user can share, move, or clear from
     * Settings between now and then.
     */
    fun stage(context: Context, source: File): Boolean {

        EventLog.init(context)

        if (!source.exists()) return false

        return try {
            source.copyTo(pendingFile(context), overwrite = true)
            EventLog.log(TAG, "Staged ${source.name} (${source.length()} bytes) for restore")
            true
        } catch (e: IOException) {
            EventLog.log(TAG, "Could not stage ${source.name}: ${e.message}")
            false
        }
    }

    /**
     * Puts a staged file in place, if there is one. Returns a one-line
     * summary for the Advanced screen, or null on the launches where
     * there is nothing to do - which is every launch but the one after a
     * restore was asked for.
     *
     * Must run before Room opens the database. Called from
     * `AppDatabase.build()` ahead of SchemaRescue, so the file the rest
     * of the startup sees is the restored one.
     */
    fun apply(context: Context): String? {

        EventLog.init(context)

        val pending = pendingFile(context)
        if (!pending.exists()) return null

        val live = context.getDatabasePath(SchemaRescue.DB_NAME)

        return try {

            live.parentFile?.mkdirs()
            pending.copyTo(live, overwrite = true)

            // The write-ahead log and shared-memory file belong to the
            // database that was just replaced. Left behind, SQLite would
            // try to replay them over a file they don't match.
            File(live.path + "-wal").delete()
            File(live.path + "-shm").delete()

            // The live-ride marker names a row in the database that just
            // went away. Left set, service create would go looking for an
            // interrupted ride that the restored file has no row for.
            LiveRideStore(context).clear()

            pending.delete()

            EventLog.log(TAG, "Restored ${live.length()} bytes from a staged backup")
            "Restored from backup on this launch."

        } catch (e: IOException) {
            // The live database is untouched or half-written; either way
            // the staged file stays put, so the next launch tries again
            // rather than losing the only copy.
            EventLog.log(TAG, "Could not restore: ${e.message}")
            "A restore was staged but could not be applied - see the event log."
        }
    }

    /** Whether a restore is waiting for the next launch. */
    fun isPending(context: Context): Boolean = pendingFile(context).exists()
}
