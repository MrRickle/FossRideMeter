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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration configuration, checked without a device.
 *
 * [migrationsCoverEveryVersion] is the one that earns its keep: bump
 * [SCHEMA_VERSION] without writing the migration to go with it and this
 * fails on `./gradlew test`, seconds after the edit, instead of on a
 * phone that has already dropped its tables. It is deliberately a JVM
 * test rather than an instrumented one so that nothing - no emulator,
 * no attached phone - stands between making that mistake and hearing
 * about it.
 *
 * What Room actually does with the migrations is a different question
 * and needs a real SQLite; that is MigrationTest, in androidTest.
 */
class MigrationConfigTest {

    @Test
    fun migrationsCoverEveryVersion() {
        for (version in MIGRATION_BASELINE until SCHEMA_VERSION) {
            val covered = MIGRATIONS.any {
                it.startVersion <= version && version < it.endVersion
            }
            assertTrue(
                "No migration covers $version -> ${version + 1}. Bumping " +
                        "SCHEMA_VERSION to $SCHEMA_VERSION needs one written " +
                        "in Migrations.kt, and schemas/$SCHEMA_VERSION.json " +
                        "committed with it.",
                covered
            )
        }
    }

    /** Nothing may start below the baseline: there is no schema to validate it against. */
    @Test
    fun noMigrationStartsBelowTheBaseline() {
        MIGRATIONS.forEach {
            assertTrue(
                "Migration ${it.startVersion} -> ${it.endVersion} starts below " +
                        "the baseline $MIGRATION_BASELINE, where no exported schema exists.",
                it.startVersion >= MIGRATION_BASELINE
            )
        }
    }

    /** The destructive fallback covers everything below the baseline, and only that. */
    @Test
    fun preBaselineVersionsStopAtTheBaseline() {
        assertEquals(
            (1 until MIGRATION_BASELINE).toList(),
            PRE_BASELINE_VERSIONS.toList()
        )
    }

    @Test
    fun aDatabaseOlderThanTheBaselineWasRebuilt() {
        assertTrue(rebuiltFromScratch(MIGRATION_BASELINE - 1))
        assertTrue(rebuiltFromScratch(1))
    }

    /** A downgrade: the schema on disk is one this build has never heard of. */
    @Test
    fun aDatabaseNewerThanThisBuildWasRebuilt() {
        assertTrue(rebuiltFromScratch(SCHEMA_VERSION + 1))
    }

    /** Everything from the baseline to now is migrated, so the rows are already across. */
    @Test
    fun aMigratableDatabaseWasNotRebuilt() {
        for (version in MIGRATION_BASELINE..SCHEMA_VERSION) {
            assertFalse(
                "A database on $version is migrated, not rebuilt - carrying rows " +
                        "into it would duplicate what the migration already moved.",
                rebuiltFromScratch(version)
            )
        }
    }

    /** An unreadable version is a file to leave alone, not one to write into. */
    @Test
    fun anUnknownVersionIsNotTreatedAsRebuilt() {
        assertFalse(rebuiltFromScratch(null))
    }
}
