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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.util.AppVersion
import org.fossridemeter.app.util.ProjectLinks
import org.fossridemeter.app.util.openLink

@Composable
fun AboutScreen() {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Text(
            text = "FossRideMeter",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        // The version is what a user is looking for - "which one am I
        // running" - and it was the one thing this screen didn't show:
        // AppVersion.versionName existed and nothing called it, so every
        // release up to 0.1.3 showed a commit hash and a date and no
        // version at all.
        Text(
            text = "Version ${AppVersion.versionName}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(4.dp))

        // The build stamp stays, one step quieter. It is for tracing an
        // installed APK back to a commit, which matters to whoever is
        // reading a bug report rather than to whoever is filing it.
        Text(
            text = "${AppVersion.gitVersion} · ${AppVersion.buildTime}",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        Text("Copyright © 2026 Rick Hallock")

        Spacer(Modifier.height(12.dp))

        Text(
            "FossRideMeter is free software licensed under the GNU General Public License version 3 or later."
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Source code available on GitHub.",
            modifier = Modifier.clickable {
                openLink(context, ProjectLinks.REPO, "FossRideMeter")
            }
        )

        Spacer(Modifier.height(16.dp))

        // Opens the releases page rather than checking for an update.
        // The app has no INTERNET permission and is not getting one for
        // this - see ProjectLinks - so it cannot know whether a newer
        // version exists. What it can do is put the answer one tap from
        // the version above it, which is the comparison being made
        // anyway.
        OutlinedButton(
            onClick = {
                openLink(context, ProjectLinks.LATEST_RELEASE, "FossRideMeter releases")
            }
        ) {
            Text("Check for updates")
        }

        Text(
            text = "Opens the releases page in your browser. The app has no " +
                "internet access of its own, so it cannot check for you.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Draws nothing at all - divider included - until a donation
        // method is configured. See DonateSection.
        DonateSection(modifier = Modifier.padding(top = 24.dp))
    }
}
