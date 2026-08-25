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
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What the rescue carries, and what it doesn't.
 *
 * These run against a database named for the test, never `rides.db` -
 * the instrumented target is the app itself, and a test that opened the
 * real file would be a test that deleted somebody's rides.
 *
 * Two of the three pin down limits rather than features. That is on
 * purpose: [aTableGainingANotNullColumnComesBackEmpty] is the case
 * CLAUDE.md says to check a schema change against, and the number it
 * loses is every row in the table, not the odd one. Now that upgrades
 * are migrated it shouldn't arise - it is what happens when the rescue
 * is reached at all.
 */
@RunWith(AndroidJUnit4::class)
class SchemaRescueTest {

    private companion object {

        const val DEST_DB = "rescue-test.db"

        val PLACES_NOW = """
            CREATE TABLE places (
                id TEXT NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL,
                longitude REAL NOT NULL, radiusMeters REAL NOT NULL,
                geohash TEXT NOT NULL, isNamed INTEGER NOT NULL,
                locationLocked INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                autoStart INTEGER NOT NULL, autoSave INTEGER NOT NULL,
                PRIMARY KEY(id))
        """

        /** The same table before autoStart/autoSave existed. */
        val PLACES_BEFORE_AUTO = """
            CREATE TABLE places (
                id TEXT NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL,
                longitude REAL NOT NULL, radiusMeters REAL NOT NULL,
                geohash TEXT NOT NULL, isNamed INTEGER NOT NULL,
                locationLocked INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                PRIMARY KEY(id))
        """

        /** A latitude that only has to round once to move the place. */
        const val LATITUDE = 37.774929123456
    }

    private lateinit var context: Context
    private lateinit var backup: File
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DEST_DB)
        backup = File(context.cacheDir, "rescue-source.db").also { it.delete() }
        database = Room.databaseBuilder(context, AppDatabase::class.java, DEST_DB).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DEST_DB)
        backup.delete()
    }

    /** The ordinary case: the tables are the same, the rows come across. */
    @Test
    fun carriesEveryRowForward() {

        writeSource(PLACES_NOW, places = 3)

        val summary = SchemaRescue.carryForward(context, writable(), backup)

        assertEquals(3, countIn("places"))
        assertEquals("3 places", summary.substringBefore(";"))

        // Read back through Room rather than as text: a REAL written as
        // a string and parsed back is the conversion this is here to
        // catch.
        val place = runBlocking { database.placeDao().getById("place-0") }
        assertEquals(LATITUDE, place!!.latitude, 0.0)
    }

    /**
     * A column added since, NOT NULL and with no default - which is every
     * column this schema has ever added. The insert can't satisfy it, so
     * the table arrives empty. The set-aside file is what makes that
     * recoverable, and why it is kept.
     */
    @Test
    fun aTableGainingANotNullColumnComesBackEmpty() {

        writeSource(PLACES_BEFORE_AUTO, places = 3)

        val summary = SchemaRescue.carryForward(context, writable(), backup)

        assertEquals(0, countIn("places"))
        assertEquals("0 places (3 lost)", summary.substringBefore(";"))
    }

    /**
     * The guard on the guard. AppDatabase only calls this when Room threw
     * the tables away, but if that ever slipped, copying on top of rows a
     * migration had already moved would duplicate every one of them.
     */
    @Test
    fun refusesATableThatAlreadyHasRows() {

        writeSource(PLACES_NOW, places = 3)

        writable().execSQL(
            "INSERT INTO places VALUES ('kept', 'Home', 1.0, 2.0, 50.0, 'abc', 1, 0, 0, 0, 0)"
        )

        SchemaRescue.carryForward(context, writable(), backup)

        assertEquals(1, countIn("places"))
        assertEquals("Home", runBlocking { database.placeDao().getById("kept") }?.name)
    }

    /** Builds the set-aside file: [createSql]'s places table, [places] rows in it. */
    private fun writeSource(createSql: String, places: Int) {
        SQLiteDatabase.openOrCreateDatabase(backup, null).use { old ->
            old.execSQL(createSql)
            repeat(places) { index ->
                val columns = if (createSql.contains("autoStart")) 11 else 9
                val values = listOf(
                    "'place-$index'", "'Place $index'", "$LATITUDE", "-122.4194",
                    "50.0", "'9q8yy'", "1", "0", "1700000000000", "0", "0"
                ).take(columns).joinToString(", ")
                old.execSQL("INSERT INTO places VALUES ($values)")
            }
            old.version = MIGRATION_BASELINE - 1
        }
    }

    private fun writable() = database.openHelper.writableDatabase

    private fun countIn(table: String): Int =
        writable().query("SELECT count(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
