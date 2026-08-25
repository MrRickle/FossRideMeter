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

import android.database.Cursor
import android.database.SQLException
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossridemeter.app.util.EventLog
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What makes a database Room can only rebuild non-destructive.
 *
 * Room now migrates properly from [MIGRATION_BASELINE] on, so this is no
 * longer what happens on every upgrade. It is what happens on the two
 * that can't be migrated: a database older than the baseline, from
 * before schemas were exported and migrations could be written against
 * them, and a *downgrade*, where the running build has never heard of
 * the schema on disk. AppDatabase is what tells the two apart, and the
 * recovery below runs for those two only.
 *
 * Step 1 still runs on every version change, migrated or not - the copy
 * is cheap and a migration can be wrong. Step 2 is the recovery, and
 * runs only when Room really did throw the tables away:
 *
 * 1. [setAside] copies the whole database file somewhere safe *before*
 *    Room opens it. Whatever else goes wrong, the rides still exist.
 * 2. [carryForward] runs after Room has rebuilt the tables and copies
 *    every row back, column by column, for the columns the two schemas
 *    still share.
 *
 * Step 2 is best-effort by construction and says so in what it reports.
 * A column that was added keeps its default, a column that was dropped
 * is left behind, and a column that was *renamed* looks like one of each
 * - the data lands in neither, which is the case to watch for. A row
 * that won't fit at all is counted and skipped rather than aborting the
 * rest. That is the trade for not writing migrations; the set-aside file
 * from step 1 is what makes it a trade rather than a loss, because a
 * rescue that only got some of it back leaves the rest still sitting in
 * a file that can be pulled off the device.
 *
 * The set-aside files are the old schema, so restoring one into a newer
 * app just sets it aside again. They are for reading, not for putting
 * back - which is why the debug menu offers to share them out.
 */
object SchemaRescue {

    const val DB_NAME = "rides.db"

    private const val TAG = "SchemaRescue"
    private const val DIR_NAME = "pre-upgrade"

    /** Set-aside files kept before the oldest is dropped. */
    private const val KEEP = 5

    /**
     * Places first, then rides, then stops - stops carry a foreign key to
     * rides, and a row whose parent isn't in yet is a row rejected.
     */
    private val TABLES = listOf("places", "rides", "stops")

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun directory(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Newest first. */
    fun setAsideFiles(context: Context): List<File> =
        directory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * The copy taken before Room opened the file, and the schema version
     * that file was on. [fromVersion] is null when it couldn't be read.
     *
     * The version travels with the copy because the caller needs it to
     * tell a rebuild from a migration, and reading it again afterwards
     * would read the *new* one.
     */
    data class SetAside(val file: File, val fromVersion: Int?)

    /**
     * Copies the database aside if its schema version isn't [current],
     * returning the copy - or null when there is nothing to do, which is
     * every launch but the one after a version change.
     *
     * This runs whether or not the change is one Room can migrate. A
     * migration that turns out to be wrong is exactly the case with
     * nothing else to fall back on.
     *
     * An unreadable version counts as a changed one. That copies a file
     * that may not have needed it, which costs some storage; the other
     * way round costs the rides.
     */
    fun setAside(context: Context, current: Int): SetAside? {

        EventLog.init(context)

        val live = context.getDatabasePath(DB_NAME)
        if (!live.exists()) return null

        val onDisk = checkpointAndReadVersion(live)
        if (onDisk == current) return null

        val from = onDisk?.toString() ?: "unknown"

        return try {
            val dir = directory(context).apply { mkdirs() }
            val copy = File(dir, "rides-v$from-${stamp.format(Date())}.db")
            live.copyTo(copy, overwrite = true)
            prune(dir)
            EventLog.log(
                TAG,
                "Schema $from -> $current: set aside ${copy.name}, ${copy.length()} bytes"
            )
            SetAside(copy, onDisk)
        } catch (e: IOException) {
            // Better to lose the rides than to refuse to start: the app
            // still opens, and the log says what happened to them.
            EventLog.log(TAG, "Could not set aside the database: ${e.message}")
            null
        }
    }

    /**
     * Copies what still fits from [backup] into the rebuilt database,
     * returning a one-line summary of what came across.
     *
     * [dest] has to be the already-open database rather than the
     * AppDatabase, because this runs during construction - the DAOs
     * aren't reachable yet, and going through them would need the very
     * schema this is recovering from.
     */
    fun carryForward(context: Context, dest: SupportSQLiteDatabase, backup: File): String {

        EventLog.init(context)

        val source = try {
            SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            EventLog.log(TAG, "Could not reopen ${backup.name}: ${e.message}")
            return "Could not read the set-aside database."
        }

        val summary = source.use { old ->
            TABLES.joinToString("; ") { table ->
                val (copied, lost) = copyTable(old, dest, table)
                if (lost == 0) "$copied $table" else "$copied $table ($lost lost)"
            }
        }

        EventLog.log(TAG, "Carried forward: $summary")
        return summary
    }

    /**
     * One table's worth, as (copied, lost).
     *
     * Every value is read back out at whatever type SQLite stored it as
     * rather than being forced through a string. SQLite would take the
     * strings - it types values, not columns - but a REAL written as
     * "37.7749" and read back by Room as a Double is a conversion that
     * only has to round once to move a place.
     *
     * Two things here are deliberate and were both wrong first time.
     *
     * The insert is a plain INSERT and not INSERT OR IGNORE. OR IGNORE
     * suppresses constraint violations instead of raising them, so a
     * column added since as NOT NULL with no default - which every old
     * row violates - returns success and writes nothing. That version
     * reported ten rides carried into a table that had none in it.
     *
     * And the count comes from the destination afterwards rather than
     * from counting successful calls. What the user is told is then
     * the number of rows actually in the table, whatever the insert
     * path did or didn't do, which is the only number worth printing.
     */
    private fun copyTable(
        source: SQLiteDatabase,
        dest: SupportSQLiteDatabase,
        table: String,
    ): Pair<Int, Int> {

        // Belt and braces on the caller's gate. Room only empties these
        // tables on the two paths AppDatabase calls this for, so anything
        // already here came across a migration - and copying rows in on
        // top of that would duplicate every one, or, since the ids come
        // too, fail on every insert and count the whole table as lost.
        val already = countOf(dest, table)
        if (already > 0) {
            EventLog.log(TAG, "$table already holds $already rows; not copying into it")
            return already to 0
        }

        val was = columnsOf(table) { source.rawQuery(it, null) }
        val now = columnsOf(table) { dest.query(it) }
        val shared = was.filter { it in now }
        if (shared.isEmpty()) return 0 to 0

        val columns = shared.joinToString(", ") { "\"$it\"" }
        val slots = shared.joinToString(", ") { "?" }
        val insert = "INSERT INTO \"$table\" ($columns) VALUES ($slots)"

        var seen = 0

        source.rawQuery("SELECT $columns FROM \"$table\"", null).use { row ->
            while (row.moveToNext()) {

                seen++

                val values = Array<Any?>(shared.size) { index ->
                    when (row.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> row.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> row.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> row.getBlob(index)
                        else -> row.getString(index)
                    }
                }

                try {
                    dest.execSQL(insert, values)
                } catch (e: SQLException) {
                    // A column added since with no default, or a foreign
                    // key whose target didn't make it. One row failing
                    // says nothing about the next, so this counts and
                    // carries on rather than abandoning the table.
                }
            }
        }

        val copied = countOf(dest, table)
        return copied to (seen - copied)
    }

    /** Rows actually in [table] now - the number the user is shown. */
    private fun countOf(dest: SupportSQLiteDatabase, table: String): Int =
        try {
            dest.query("SELECT count(*) FROM \"$table\"").use { row ->
                if (row.moveToFirst()) row.getInt(0) else 0
            }
        } catch (e: SQLException) {
            0
        }

    /** Column names of [table], or empty if the table isn't there at all. */
    private fun columnsOf(table: String, query: (String) -> Cursor): List<String> =
        try {
            query("PRAGMA table_info(\"$table\")").use { row ->
                buildList {
                    val name = row.getColumnIndex("name")
                    while (row.moveToNext()) add(row.getString(name))
                }
            }
        } catch (e: SQLException) {
            emptyList()
        }

    /**
     * Reads the schema version, folding the write-ahead log into the file
     * first so the copy taken next is the whole database rather than most
     * of it. That needs a writable handle, which is the only reason this
     * doesn't open read-only.
     */
    private fun checkpointAndReadVersion(file: File): Int? =
        try {
            SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { /* run it */ }
                db.version
            }
        } catch (e: SQLiteException) {
            null
        }

    /** Keeps the newest [KEEP]; an upgrade every week shouldn't fill the phone. */
    private fun prune(dir: File) {
        dir.listFiles { file -> file.isFile && file.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }
}
