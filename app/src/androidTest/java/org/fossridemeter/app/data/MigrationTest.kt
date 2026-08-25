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

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migrations, run against a real SQLite on a real device.
 *
 * `MigrationConfigTest`, the JVM test, checks that a migration exists for
 * every version - it is in src/test, which this source set cannot see, so
 * the name is plain text rather than a link. This checks the other half:
 * that running them lands on the schema Room expects. The
 * helper reads the exported JSON from androidTest assets - the Room
 * Gradle plugin puts app/schemas there - and compares the database it
 * ends up with against the current one, column by column and index by
 * index. A stale exported schema, a migration that adds a column with
 * the wrong type, or one that forgets an index all fail here.
 *
 * That comparison is also the reason this can't be a JVM test: it is
 * SQLite's account of the tables, not Room's.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /**
     * The oldest database a migration can start from, walked all the way
     * up. With the baseline still equal to [SCHEMA_VERSION] this
     * validates the exported schema against the entities and no more -
     * which is worth having on its own, and becomes the real test the
     * first time a version is added.
     */
    @Test
    fun migratesFromTheBaselineToCurrent() {
        helper.createDatabase(TEST_DB, MIGRATION_BASELINE).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            SCHEMA_VERSION,
            true,
            *MIGRATIONS
        ).close()
    }
}
