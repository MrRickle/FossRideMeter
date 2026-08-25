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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.DistanceProviderType
import org.fossridemeter.app.model.DistanceUnit
import org.fossridemeter.app.model.MeasurementSystem
import org.fossridemeter.app.model.WatchAccuracy

private val Context.dataStore by preferencesDataStore(
    name = "settings"
)

class SettingsRepository(
    private val context: Context
) {

    private object Keys {
        val perMeterRate = doublePreferencesKey("per_meter_rate")
        val hourlyRate = doublePreferencesKey("per_hour_rate")
        val stoppedHourlyRate = doublePreferencesKey("stopped_hourly_rate")
        val distanceProvider = stringPreferencesKey("distance_provider")
        val distanceUnit = stringPreferencesKey("distance_unit")
        val minimumSpeedMps = doublePreferencesKey("minimum_speed_mps")
        val measurementSystem = stringPreferencesKey("measurement_system")
        val baseAmount = doublePreferencesKey("base_amount")
        val minimumAmount = doublePreferencesKey("minimum_amount")
        val stopDetectionMinutes = intPreferencesKey("stop_detection_minutes")
        val autoWatchSeconds = intPreferencesKey("auto_watch_seconds")
        val autoSaveGraceMinutes = intPreferencesKey("auto_save_grace_minutes")
        val autoWatchAccuracy = stringPreferencesKey("auto_watch_accuracy")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val startSoundUri = stringPreferencesKey("start_sound_uri")
        val pauseSoundUri = stringPreferencesKey("pause_sound_uri")
        val resumeSoundUri = stringPreferencesKey("resume_sound_uri")
        val saveSoundUri = stringPreferencesKey("save_sound_uri")
        val cancelSoundUri = stringPreferencesKey("cancel_sound_uri")
    }

    /**
     * What an absent preference falls back to.
     *
     * Taken from `Settings()` rather than repeated as literals, because
     * these have to agree with it and there is no way to notice when they
     * stop: a fresh install reads the data class, and an install whose
     * preferences predate a key reads this. They were the same wrong
     * number for a long time.
     */
    private val defaults = Settings()

    val settings: Flow<Settings> =
        context.dataStore.data.map { preferences ->
            Settings(
                perMeterRate =
                    preferences[Keys.perMeterRate] ?: defaults.perMeterRate,
                hourlyRate =
                    preferences[Keys.hourlyRate] ?: defaults.hourlyRate,
                stoppedHourlyRate =
                    preferences[Keys.stoppedHourlyRate] ?: defaults.stoppedHourlyRate,
                distanceProvider =
                    DistanceProviderType.valueOf(
                        preferences[Keys.distanceProvider]
                            ?: defaults.distanceProvider.name
                    ),
                distanceUnit =
                    DistanceUnit.valueOf(
                        preferences[Keys.distanceUnit]
                            ?: defaults.distanceUnit.name
                    ),
                minimumSpeedMps =
                    (preferences[Keys.minimumSpeedMps] ?: defaults.minimumSpeedMps),
                measurementSystem =
                    MeasurementSystem.valueOf(
                        preferences[Keys.measurementSystem]
                            ?: defaults.measurementSystem.name
                    ),
                baseAmount =
                    preferences[Keys.baseAmount] ?: defaults.baseAmount,
                minimumAmount =
                    preferences[Keys.minimumAmount] ?: defaults.minimumAmount,
                stopDetectionMinutes =
                    preferences[Keys.stopDetectionMinutes] ?: defaults.stopDetectionMinutes,
                autoWatchSeconds =
                    preferences[Keys.autoWatchSeconds] ?: defaults.autoWatchSeconds,
                autoSaveGraceMinutes =
                    preferences[Keys.autoSaveGraceMinutes] ?: defaults.autoSaveGraceMinutes,
                autoWatchAccuracy =
                    WatchAccuracy.valueOf(
                        preferences[Keys.autoWatchAccuracy]
                            ?: defaults.autoWatchAccuracy.name
                    ),
                soundEnabled =
                    preferences[Keys.soundEnabled] ?: defaults.soundEnabled,
                // Absent means the built-in tone, so these stay null
                // rather than defaulting to anything.
                startSoundUri = preferences[Keys.startSoundUri],
                pauseSoundUri = preferences[Keys.pauseSoundUri],
                resumeSoundUri = preferences[Keys.resumeSoundUri],
                saveSoundUri = preferences[Keys.saveSoundUri],
                cancelSoundUri = preferences[Keys.cancelSoundUri],
            )
        }

    suspend fun update(
        settings: Settings
    ) {
        context.dataStore.edit { preferences ->
            preferences[Keys.perMeterRate] =
                settings.perMeterRate
            preferences[Keys.hourlyRate] =
                settings.hourlyRate
            preferences[Keys.stoppedHourlyRate] =
                settings.stoppedHourlyRate
            preferences[Keys.distanceProvider] =
                settings.distanceProvider.name
            preferences[Keys.distanceUnit] =
                settings.distanceUnit.name
            preferences[Keys.minimumSpeedMps] =
                settings.minimumSpeedMps
            preferences[Keys.measurementSystem] =
                settings.measurementSystem.name
            preferences[Keys.baseAmount] =
                settings.baseAmount
            preferences[Keys.minimumAmount] =
                settings.minimumAmount
            preferences[Keys.stopDetectionMinutes] =
                settings.stopDetectionMinutes
            preferences[Keys.autoWatchSeconds] =
                settings.autoWatchSeconds
            preferences[Keys.autoSaveGraceMinutes] =
                settings.autoSaveGraceMinutes
            preferences[Keys.autoWatchAccuracy] =
                settings.autoWatchAccuracy.name
            preferences[Keys.soundEnabled] =
                settings.soundEnabled

            // A null is the absence of a choice, not a value to store -
            // writing one would mean "no sound" rather than "the built-in
            // one", so the key is removed instead.
            fun putOrRemove(key: Preferences.Key<String>, value: String?) {
                if (value == null) preferences.remove(key)
                else preferences[key] = value
            }

            putOrRemove(Keys.startSoundUri, settings.startSoundUri)
            putOrRemove(Keys.pauseSoundUri, settings.pauseSoundUri)
            putOrRemove(Keys.resumeSoundUri, settings.resumeSoundUri)
            putOrRemove(Keys.saveSoundUri, settings.saveSoundUri)
            putOrRemove(Keys.cancelSoundUri, settings.cancelSoundUri)
        }
    }
}
