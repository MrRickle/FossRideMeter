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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Stop

/**
 * Bump this whenever an entity changes - and add the matching migration
 * to [MIGRATIONS] in the same commit, because from [MIGRATION_BASELINE]
 * on, a version with no migration for it is a crash on open, not a wipe.
 *
 * It is a top-level const rather than a literal in the annotation because
 * SchemaRescue needs the same number to tell an upgraded database from a
 * current one.
 */
const val SCHEMA_VERSION = 7

@Database(
    entities = [RideRecord::class, Place::class, Stop::class],
    version = SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rideDao(): RideDao
    abstract fun placeDao(): PlaceDao
    abstract fun stopDao(): StopDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** What the last upgrade managed to carry over, for the Advanced screen. */
        @Volatile
        var lastRescue: String? = null
            private set

        /** Whether this launch was the one that applied a staged restore. */
        @Volatile
        var lastRestore: String? = null
            private set

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Upgrades from [MIGRATION_BASELINE] on are real migrations - a
         * version in that range with no `Migration` for it now throws
         * instead of quietly dropping the tables. Only a database from
         * before the baseline, or from a *newer* build than this one,
         * is still rebuilt from scratch, and those two are exactly the
         * cases SchemaRescue exists for.
         */
        private fun build(context: Context): AppDatabase {

            // Before anything reads the file: a restore asked for in the
            // last process is put in place here, so what the migrations
            // and SchemaRescue below see is the restored database rather
            // than the one it replaced.
            lastRestore = PendingRestore.apply(context)

            val setAside = SchemaRescue.setAside(context, SCHEMA_VERSION)

            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                SchemaRescue.DB_NAME
            )
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(
                    dropAllTables = true,
                    *PRE_BASELINE_VERSIONS
                )
                // A downgrade can't be migrated - the older build has
                // never heard of the newer schema - so it keeps the old
                // behaviour, rescue and all.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()

            if (setAside != null && rebuiltFromScratch(setAside.fromVersion)) {
                // Asking for the writable database is what makes Room open
                // the file, and opening it is what drops the old tables.
                // The rescue has to run after that and before anything
                // else reads, so it is forced here rather than left to
                // happen at whatever the first query turns out to be.
                lastRescue = SchemaRescue.carryForward(
                    context,
                    database.openHelper.writableDatabase,
                    setAside.file
                )
            }

            return database
        }

        /**
         * Drops the cached instance so the next getInstance() call opens a
         * fresh connection. Needed after directly replacing the underlying
         * .db file (e.g. DbBackupUtils.restore) since the old instance is
         * still holding a handle to the file that was just overwritten.
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
