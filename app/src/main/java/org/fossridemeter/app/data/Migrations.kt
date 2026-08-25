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

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The oldest schema a real migration can start from.
 *
 * Version 6 is the first one with an exported `schemas/6.json`, and a
 * migration that can't be validated against a recorded schema isn't one -
 * so nothing older gets a [Migration] written for it. A database below
 * this version is still rebuilt from scratch and rescued row by row by
 * [SchemaRescue], which is what every version of this app up to now did
 * with every upgrade.
 *
 * Every install is on 6, so in practice the destructive path is dead code
 * kept for a phone that was sitting in a drawer through the change. Don't
 * lower this number to make an old database open; write the migration.
 */
const val MIGRATION_BASELINE = 6

/**
 * Adds the two columns the stopped hourly rate needs.
 *
 * `stoppedSeconds` is NOT NULL, so SQLite requires a default to add it to
 * a table that already has rows - and the entity declares the same
 * default with `@ColumnInfo(defaultValue = "0")`, because Room compares
 * the two and reports a mismatch as a migration that "didn't properly
 * handle" the version. Zero is also the right answer for a ride recorded
 * before any of this existed: none of its time was billed as stopped.
 *
 * `stoppedHourlyRate` is nullable and gets no default, so an older ride
 * reads as "-" rather than claiming a rate of zero was in force.
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE rides ADD COLUMN stoppedSeconds INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE rides ADD COLUMN stoppedHourlyRate REAL"
        )
    }
}

/**
 * Every migration from [MIGRATION_BASELINE] forward, in order.
 *
 * Adding one:
 *
 * 1. Change the entity.
 * 2. Bump [SCHEMA_VERSION].
 * 3. Add a `Migration(n, n + 1)` here that does the same thing in SQL.
 * 4. Build, and commit the new `schemas/<n+1>.json` with the change - the
 *    build writes it, and a schema that isn't committed is a migration
 *    that can't be tested later.
 *
 * A missing migration from the baseline up is a crash on open rather than
 * a silent wipe, and `MigrationConfigTest` fails before that: it checks
 * every version from the baseline to current is covered, on
 * `./gradlew test`, with no device in the way.
 */
val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_6_7,
)

/**
 * The versions Room is still allowed to rebuild from scratch: everything
 * below the baseline.
 */
val PRE_BASELINE_VERSIONS: IntArray = (1 until MIGRATION_BASELINE).toList().toIntArray()

/**
 * Whether Room threw the tables away rather than migrating them, given
 * the version that was on disk before it opened the file.
 *
 * This is what keeps [SchemaRescue.carryForward] from running after a
 * migration that already brought the rows across: copying them in on
 * top would duplicate every one, or, since the ids come too, fail on
 * every insert and report a loss that never happened.
 *
 * The destructive paths are exactly the two AppDatabase configures -
 * below the baseline, or a downgrade from a build newer than this one.
 * An unreadable version is neither: it is a file this can't reason
 * about, so nothing is written into it and the set-aside copy stands as
 * the backstop.
 *
 * It lives here rather than in AppDatabase because it is the same
 * boundary [MIGRATION_BASELINE] and [PRE_BASELINE_VERSIONS] draw, said
 * a third way - and because a boundary is worth a test.
 */
internal fun rebuiltFromScratch(from: Int?): Boolean =
    from != null && (from < MIGRATION_BASELINE || from > SCHEMA_VERSION)
