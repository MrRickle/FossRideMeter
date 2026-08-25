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
package org.fossridemeter.app.ui

import android.content.res.Configuration
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import org.fossridemeter.app.model.DistanceProviderType
import org.fossridemeter.app.model.Settings
import org.fossridemeter.app.model.WatchAccuracy
import org.fossridemeter.app.ui.theme.FossRideMeterTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import org.fossridemeter.app.model.MeasurementSystem
import org.fossridemeter.app.model.TimeUnit
import org.fossridemeter.app.util.abbreviation
import org.fossridemeter.app.util.displaySpeedRateAbbreviation
import org.fossridemeter.app.util.distanceToUnit
import org.fossridemeter.app.util.distanceUnit
import org.fossridemeter.app.util.toDisplayRate
import org.fossridemeter.app.util.toPerMeterRate
import org.fossridemeter.app.util.unitToDistance


@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChanged: (Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    BackHandler {
        onBack()
    }

    val landscape =
        LocalConfiguration.current.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

    val settingsFieldTextStyle =
        LocalTextStyle.current.copy(
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = 1.0f
            )
        )


    if (landscape) {

        SettingsLandscape(
            settings = settings,
            settingsFieldTextStyle = settingsFieldTextStyle,
            onSettingsChanged = onSettingsChanged,
            onBack = onBack,
            modifier = modifier
        )

    } else {

        SettingsPortrait(
            settings = settings,
            settingsFieldTextStyle = settingsFieldTextStyle,
            onSettingsChanged = onSettingsChanged,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
private fun SettingsPortrait(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(2.dp)
        ) {

            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(2.dp))

            SettingsRatesSection(
                settings,
                settingsFieldTextStyle,
                onSettingsChanged
            )

            Spacer(Modifier.height(2.dp))

            SettingsDistanceSection(
                settings,
                settingsFieldTextStyle,
                onSettingsChanged
            )

            Spacer(Modifier.height(2.dp))

            SettingsStopsSection(
                settings,
                settingsFieldTextStyle,
                onSettingsChanged
            )

            Spacer(Modifier.height(2.dp))

            SettingsAutoSection(
                settings,
                settingsFieldTextStyle,
                onSettingsChanged
            )

            Spacer(Modifier.height(2.dp))

            SettingsUnitsSection(
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )

            Spacer(Modifier.height(2.dp))

            SettingsSoundsSection(
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )

        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SettingsLandscape(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Text(
                "SETTINGS",
                style = MaterialTheme.typography.headlineMedium,

                )

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    SettingsRatesSection(
                        settings,
                        settingsFieldTextStyle,
                        onSettingsChanged
                    )
                }

                Spacer(Modifier.width(6.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    SettingsDistanceSection(
                        settings,
                        settingsFieldTextStyle,
                        onSettingsChanged
                    )

                    Spacer(Modifier.height(6.dp))

                    SettingsStopsSection(
                        settings,
                        settingsFieldTextStyle,
                        onSettingsChanged
                    )

                    Spacer(Modifier.height(6.dp))

                    SettingsAutoSection(
                        settings,
                        settingsFieldTextStyle,
                        onSettingsChanged
                    )

                    Spacer(Modifier.height(6.dp))

                    SettingsUnitsSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged
                    )

                    Spacer(Modifier.height(6.dp))

                    SettingsSoundsSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged
                    )
                }

            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SettingsRatesSection(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        "Rates",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    val unit = settings.measurementSystem.distanceUnit

    var displayRate by remember {
        mutableStateOf("%.2f".format(unit.toDisplayRate(settings.perMeterRate)))
    }

    LaunchedEffect(settings.perMeterRate, unit) {
        val current = displayRate.toDoubleOrNull()
        val fromSettings = unit.toDisplayRate(settings.perMeterRate)
        if (current == null || kotlin.math.abs(current - fromSettings) > 0.001) {
            displayRate = "%.2f".format(fromSettings)
        }
    }

    OutlinedTextField(
        value = displayRate,
        textStyle = settingsFieldTextStyle,
        onValueChange = { input ->
            displayRate = input
            input.toDoubleOrNull()?.let { enteredRate ->
                onSettingsChanged(
                    settings.copy(perMeterRate = unit.toPerMeterRate(enteredRate))
                )
            }
        },
        label = {
            Text("Per ${unit.abbreviation()}")
        },
        singleLine = true,
    )

    Spacer(Modifier.height(8.dp))

    var hourlyRate by remember {
        mutableStateOf("%.2f".format(settings.hourlyRate))
    }

    LaunchedEffect(settings.hourlyRate) {
        val current = hourlyRate.toDoubleOrNull()
        if (current == null || kotlin.math.abs(current - settings.hourlyRate) > 0.001) {
            hourlyRate = "%.2f".format(settings.hourlyRate)
        }
    }

    OutlinedTextField(
        value = hourlyRate,
        textStyle = settingsFieldTextStyle,
        onValueChange = {
            hourlyRate = it
            it.toDoubleOrNull()?.let { rate ->
                onSettingsChanged(
                    settings.copy(hourlyRate = rate)
                )
            }
        },
        label = {
            Text("Per Hour")
        },
        singleLine = true,
    )

    Spacer(Modifier.height(8.dp))

    var stoppedHourlyRate by remember {
        mutableStateOf("%.2f".format(settings.stoppedHourlyRate))
    }

    LaunchedEffect(settings.stoppedHourlyRate) {
        val current = stoppedHourlyRate.toDoubleOrNull()
        if (current == null ||
            kotlin.math.abs(current - settings.stoppedHourlyRate) > 0.001
        ) {
            stoppedHourlyRate = "%.2f".format(settings.stoppedHourlyRate)
        }
    }

    OutlinedTextField(
        value = stoppedHourlyRate,
        textStyle = settingsFieldTextStyle,
        onValueChange = {
            stoppedHourlyRate = it
            it.toDoubleOrNull()?.let { rate ->
                onSettingsChanged(
                    settings.copy(stoppedHourlyRate = rate)
                )
            }
        },
        label = {
            Text("Per Hour Stopped")
        },
        singleLine = true,
    )

    Text(
        text = "Time at a stop is billed at this rate instead. Waiting at " +
                "a light is not a stop.",
        style = MaterialTheme.typography.bodySmall
    )

    Spacer(Modifier.height(8.dp))

    var baseAmount by remember {
        mutableStateOf("%.2f".format(settings.baseAmount))
    }

    LaunchedEffect(settings.baseAmount) {
        val current = baseAmount.toDoubleOrNull()
        if (current == null || kotlin.math.abs(current - settings.baseAmount) > 0.001) {
            baseAmount = "%.2f".format(settings.baseAmount)
        }
    }

    OutlinedTextField(
        value = baseAmount,
        textStyle = settingsFieldTextStyle,
        onValueChange = {
            baseAmount = it
            it.toDoubleOrNull()?.let { rate ->
                onSettingsChanged(
                    settings.copy(baseAmount = rate)
                )
            }
        },
        label = {
            Text("Base Amount")
        },
        singleLine = true,
    )

    Spacer(Modifier.height(8.dp))

    var minimumAmount by remember {
        mutableStateOf("%.2f".format(settings.minimumAmount))
    }

    LaunchedEffect(settings.minimumAmount) {
        val current = minimumAmount.toDoubleOrNull()
        if (current == null || kotlin.math.abs(current - settings.minimumAmount) > 0.001) {
            minimumAmount = "%.2f".format(settings.minimumAmount)
        }
    }

    OutlinedTextField(
        value = minimumAmount,
        textStyle = settingsFieldTextStyle,
        onValueChange = {
            minimumAmount = it
            it.toDoubleOrNull()?.let { amount ->
                onSettingsChanged(
                    settings.copy(minimumAmount = amount)
                )
            }
        },
        label = {
            Text("Minimum Amount")
        },
        singleLine = true,
    )
}
@Composable
private fun SettingsDistanceSection(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        "Distance",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    SettingsDistanceProvider(
        settings,
        onSettingsChanged
    )

    Spacer(Modifier.height(8.dp))

    SettingsMinimumGpsSpeed(
        settings,
        settingsFieldTextStyle,
        onSettingsChanged
    )
}

@Composable
private fun SettingsStopsSection(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        "Stops",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    var stopMinutes by remember {
        mutableStateOf(settings.stopDetectionMinutes.toString())
    }

    LaunchedEffect(settings.stopDetectionMinutes) {
        val current = stopMinutes.toIntOrNull()
        if (current == null || current != settings.stopDetectionMinutes) {
            stopMinutes = settings.stopDetectionMinutes.toString()
        }
    }

    OutlinedTextField(
        value = stopMinutes,
        textStyle = settingsFieldTextStyle,
        onValueChange = { input ->
            stopMinutes = input
            input.toIntOrNull()?.let { minutes ->
                if (minutes >= 1) {
                    onSettingsChanged(
                        settings.copy(stopDetectionMinutes = minutes)
                    )
                }
            }
        },
        label = {
            Text("Stop Detection (minutes)")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        supportingText = {
            Text("How long stationary before it counts as a stop")
        }
    )
}


@Composable
private fun SettingsAutoSection(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        "Automatic",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    // There is no on/off switch here on purpose. Which places start and
    // save rides is set per place, in the place editor, and watching is
    // on whenever any place asks for it - a master switch would have to
    // be remembered, and the whole point is the mornings it wouldn't be.
    // These two are the cost and the safety margin of that, nothing more.
    //
    // Saying so here is the concession to that: the switch a user comes
    // looking for is missing, and a settings screen that simply doesn't
    // mention it reads as an oversight rather than a decision. The force
    // stop route is included because it is the honest answer to "stop it
    // for good without uninstalling" - a stopped package gets no
    // BOOT_COMPLETED, so BootReceiver cannot re-arm it until the app is
    // launched by hand.
    Text(
        text = "Watching runs whenever any place has auto-start or auto-save " +
            "set - there is no switch here, because a switch would have to be " +
            "remembered. Clear those flags in the place editor to stop it. To " +
            "hold it without changing places, force stop FossRideMeter in " +
            "Android's app settings; it stays off, reboots included, until you " +
            "open the app again.",
        style = MaterialTheme.typography.bodySmall
    )

    Spacer(Modifier.height(6.dp))

    var watchSeconds by remember {
        mutableStateOf(settings.autoWatchSeconds.toString())
    }

    LaunchedEffect(settings.autoWatchSeconds) {
        val current = watchSeconds.toIntOrNull()
        if (current == null || current != settings.autoWatchSeconds) {
            watchSeconds = settings.autoWatchSeconds.toString()
        }
    }

    OutlinedTextField(
        value = watchSeconds,
        textStyle = settingsFieldTextStyle,
        onValueChange = { input ->
            watchSeconds = input
            input.toIntOrNull()?.let { seconds ->
                if (seconds >= 15) {
                    onSettingsChanged(
                        settings.copy(autoWatchSeconds = seconds)
                    )
                }
            }
        },
        label = {
            Text("Location Check (seconds)")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        supportingText = {
            Text("How often to check for leaving an auto-start place")
        }
    )

    Spacer(Modifier.height(6.dp))

    var graceMinutes by remember {
        mutableStateOf(settings.autoSaveGraceMinutes.toString())
    }

    LaunchedEffect(settings.autoSaveGraceMinutes) {
        val current = graceMinutes.toIntOrNull()
        if (current == null || current != settings.autoSaveGraceMinutes) {
            graceMinutes = settings.autoSaveGraceMinutes.toString()
        }
    }

    OutlinedTextField(
        value = graceMinutes,
        textStyle = settingsFieldTextStyle,
        onValueChange = { input ->
            graceMinutes = input
            input.toIntOrNull()?.let { minutes ->
                if (minutes >= 1) {
                    onSettingsChanged(
                        settings.copy(autoSaveGraceMinutes = minutes)
                    )
                }
            }
        },
        label = {
            Text("Auto-save Grace (minutes)")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        supportingText = {
            Text("Time to resume before an arrival saves the ride")
        }
    )

    Spacer(Modifier.height(6.dp))

    Text(
        "Location Check Accuracy",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    WatchAccuracy.entries.forEach { accuracy ->

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = settings.autoWatchAccuracy == accuracy,
                onClick = {
                    onSettingsChanged(
                        settings.copy(autoWatchAccuracy = accuracy)
                    )
                }
            )

            Text(
                text = accuracy.displayName,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Text(
        text = "Cheapest-first saves battery while parked but a coarse fix " +
            "often can't resolve a place, so departures are noticed late.",
        style = MaterialTheme.typography.bodySmall
    )
}


@Composable
private fun SettingsDistanceProvider(
    settings: Settings,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        "Distance Provider",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(6.dp))

    DistanceProviderType.entries.forEach { type ->

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = settings.distanceProvider == type,
                onClick = {

                    onSettingsChanged(
                        settings.copy(
                            distanceProvider = type
                        )
                    )
                }
            )

            Text(
                text = type.displayName,
                modifier = Modifier.padding(start = 8.dp)

            )
        }
    }
}


@Composable
private fun SettingsMinimumGpsSpeed(
    settings: Settings,
    settingsFieldTextStyle: TextStyle,
    onSettingsChanged: (Settings) -> Unit
) {

    var showHelp by rememberSaveable {
        mutableStateOf(false)
    }

    val unit = settings.measurementSystem.distanceUnit

    var displaySpeed by remember {
        val speed = distanceToUnit(settings.minimumSpeedMps, unit) * 3600
        mutableStateOf("%.1f".format(speed))
    }

    LaunchedEffect(settings.minimumSpeedMps, unit) {
        val current = displaySpeed.toDoubleOrNull()
        val fromSettings = distanceToUnit(settings.minimumSpeedMps, unit) * 3600
        if (current == null || kotlin.math.abs(current - fromSettings) > 0.05) {
            displaySpeed = "%.1f".format(fromSettings)
        }
    }
    OutlinedTextField(
        value = displaySpeed,
        textStyle = settingsFieldTextStyle,
        onValueChange = { input ->
            displaySpeed = input

            input.toDoubleOrNull()?.let { enteredSpeed: Double ->
                // Convert back to per-meter before storing
                val minimumSpeed: Double = unitToDistance(enteredSpeed, unit)/3600.0
                onSettingsChanged(
                    settings.copy(minimumSpeedMps = minimumSpeed)
                )
            }
        },
        label = {
            Text("Minimum Speed (${displaySpeedRateAbbreviation(unit, TimeUnit.HOURS)})")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        trailingIcon = {
            IconButton(
                onClick = {
                    showHelp = true
                }
            ) {
                Text("ⓘ")
            }
        },
        supportingText = {
            Text("0 = count all movement")
        }
    )

    if (showHelp) {
        AlertDialog(
            onDismissRequest = {
                showHelp = false
            },
            title = {
                Text("Minimum Speed")
            },
            text = {

                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                ) {

                    Text(
                        """
            Ignores movement slower than this speed.
            Approximate Rates per Second

            0.0 -> Count all movement.
            5ft or 1.5m (3.4 mph) -> Walking.
            8ft or 2.5m (5.5 mph) -> Running.
            20ft or 5m -> (14 mph)Driving
           
            Higher values require faster movement before distance is added. /
            This does not change GPS accuracy. /
            It only decides which movement counts toward ride distance.
            """.trimIndent()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHelp = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SettingsUnitsSection(
    settings: Settings,
    onSettingsChanged: (Settings) -> Unit
) {

    Text(
        text = "Units",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = settings.measurementSystem == MeasurementSystem.US,
            onClick = {
                onSettingsChanged(settings.copy(measurementSystem = MeasurementSystem.US))
            }
        )
        Text("US (miles, feet)")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = settings.measurementSystem == MeasurementSystem.SI,
            onClick = {
                onSettingsChanged(settings.copy(measurementSystem = MeasurementSystem.SI))
            }
        )
        Text("SI (kilometers, meters)")
    }
}


/** One ride action's row: its name, the sound it makes now, and how to
 * write a new choice back into settings. */
private data class ActionSound(
    val label: String,
    val uri: String?,
    val withUri: (String?) -> Settings,
)

/**
 * One row per ride action, each naming the sound it currently makes and
 * opening the system sound picker when tapped.
 *
 * The picker is the platform's own (`RingtoneManager.ACTION_RINGTONE_PICKER`),
 * so the list is the same one Android's own sound settings offer -
 * anything installed on the device, not a set this app ships. What is
 * stored is only the Uri it hands back.
 *
 * "Built-in tone" is the default and is not a sound file: it is the
 * platform tone RideSounds generates, and the five are already distinct
 * from each other. Choosing one sound for everything is possible but
 * costs the thing that makes the tones useful while driving, which is
 * telling the actions apart without looking.
 */
@Composable
private fun SettingsSoundsSection(
    settings: Settings,
    onSettingsChanged: (Settings) -> Unit
) {

    val context = LocalContext.current

    val actions = listOf(
        ActionSound("Start", settings.startSoundUri) { settings.copy(startSoundUri = it) },
        ActionSound("Pause", settings.pauseSoundUri) { settings.copy(pauseSoundUri = it) },
        ActionSound("Resume", settings.resumeSoundUri) { settings.copy(resumeSoundUri = it) },
        ActionSound("Save", settings.saveSoundUri) { settings.copy(saveSoundUri = it) },
        ActionSound("Cancel", settings.cancelSoundUri) { settings.copy(cancelSoundUri = it) },
    )

    // Which row's picker is open. The result comes back as an Intent, not
    // as anything tied to the row that launched it, so the row has to be
    // remembered across the trip out to the picker.
    var picking by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        val row = picking
        picking = null

        if (result.resultCode == Activity.RESULT_OK && row != null) {
            val uri = result.data?.let { data ->
                IntentCompat.getParcelableExtra(
                    data,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            }
            onSettingsChanged(actions[row].withUri(uri?.toString()))
        }
    }

    Text(
        text = "Sounds",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(modifier = Modifier.weight(1f)) {

            Text("Ride action tones")

            Text(
                text = "A sound when a ride starts, pauses, resumes, " +
                        "saves, or is cancelled. Follows your " +
                        "notification volume.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Switch(
            checked = settings.soundEnabled,
            onCheckedChange = {
                onSettingsChanged(settings.copy(soundEnabled = it))
            }
        )
    }

    if (!settings.soundEnabled) return

    Spacer(Modifier.height(4.dp))

    actions.forEachIndexed { index, action ->

        val label = action.label
        val uri = action.uri


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    picking = index
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                "$label sound"
                            )
                            // Only when there is one: putExtra's
                            // overloads don't need a null pushed at them.
                            uri?.let {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    it.toUri()
                                )
                            }
                            // Silent is what the switch above is for, and
                            // an action that makes no sound reads as an
                            // action that didn't happen.
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        }
                    )
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(label)

                Text(
                    text = soundTitle(context, uri),
                    color = tappableValueColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uri != null) {
                TextButton(onClick = {
                    onSettingsChanged(action.withUri(null))
                }) {
                    Text("Built-in")
                }
            }
        }
    }
}

/**
 * What to show under an action: the sound's own name, or "Built-in tone"
 * when nothing is chosen. A Uri that no longer resolves - the file
 * deleted, the app that supplied it uninstalled - says so rather than
 * showing a name for a sound that will not play; RideSounds falls back
 * to the tone in that case.
 */
private fun soundTitle(context: Context, uri: String?): String {

    if (uri == null) return "Built-in tone"

    return runCatching {
        RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context)
    }.getOrNull() ?: "Sound is missing - using the built-in tone"
}


@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    FossRideMeterTheme {
        SettingsScreen(
            settings = Settings(),
            onSettingsChanged = {},
            onBack = {}
        )
    }
}