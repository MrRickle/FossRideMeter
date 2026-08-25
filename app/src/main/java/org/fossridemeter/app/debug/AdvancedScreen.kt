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
package org.fossridemeter.app.debug

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fossridemeter.app.data.AppDatabase
import org.fossridemeter.app.data.AppRepository
import org.fossridemeter.app.data.SchemaRescue
import org.fossridemeter.app.util.AppRestart
import org.fossridemeter.app.util.EventLog
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Backup, restore, the databases an upgrade set aside, and the event log.
 *
 * It is called Advanced rather than Debug because that is what it is:
 * every item on it is something a user may genuinely need on a phone
 * that has never had adb attached to it. A backup before an experiment,
 * the file an upgrade set aside, the log that says why a ride didn't
 * start on its own - the audience for those is an interested user, not
 * only the developer, and a screen called "Debug menu" tells that user
 * the screen is not for them.
 *
 * The name is a promise, and two buttons had to change to keep it. Both
 * Restore and Repair place links used to act on a single tap; both now
 * say what they are about to do and wait to be told again. Repair is the
 * one to read: its orphan sweep cannot tell a deleted place's leftovers
 * from a ride import whose places haven't been imported yet.
 *
 * See "The Advanced screen is a user-facing screen" in docs/decisions.md.
 */
@Composable
fun AdvancedScreen(
    // A restore replaces the database the ride being metered is writing
    // into, and the process restart takes the service down with it. The
    // Settings screen locks mid-ride for a smaller reason than this one.
    rideActive: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasBackup by remember { mutableStateOf(DbBackupUtils.hasBackup(context)) }
    var backupTime by remember { mutableStateOf(DbBackupUtils.backupTime(context)) }
    val setAside = remember { SchemaRescue.setAsideFiles(context) }

    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRepair by remember { mutableStateOf(false) }

    // Same format the rides table uses, from the same locale source.
    val locale = LocalLocale.current.platformLocale
    val stamp = remember(locale) {
        SimpleDateFormat("MMM d, yyyy  h:mm a", locale)
    }

    // The automatic-by-location events, read back on return. They happen
    // while the phone is in a pocket pulling out of a drive, so adb is
    // never attached when they matter - see EventLog.
    var eventLog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Advanced")

        AppDatabase.lastRestore?.let { restore ->
            Text(
                text = restore,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(onClick = {
            DbBackupUtils.backup(context)
            hasBackup = true
            backupTime = DbBackupUtils.backupTime(context)
            Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
        }) {
            Text("Backup database")
        }

        Button(
            enabled = hasBackup && !rideActive,
            onClick = { confirmRestore = true }
        ) {
            Text("Restore from backup")
        }

        if (hasBackup && rideActive) {
            Text(
                text = "Finish or cancel the ride before restoring.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            enabled = hasBackup,
            onClick = { DbBackupUtils.shareBackup(context) }
        ) {
            Text("Share backup file")
        }

        // Deleting or editing a place now repairs the rides and stops
        // around it as it happens; this is the same work applied to a
        // database that predates that - points filed under a geohash
        // inside a place named later, and rows still pointing at a place
        // that was deleted before the deletion re-resolved anything.
        //
        // Kept manual because the orphan sweep cannot tell a deletion's
        // leftovers from a ride import whose places haven't been imported
        // yet: run it only when no half-finished import is waiting.
        Button(onClick = { confirmRepair = true }) {
            Text("Repair place links")
        }

        HorizontalDivider()

        // What the last upgrade rescued, and the files it rescued it
        // from. Both are only ever populated on the launch after a schema
        // change, so an install that has never been upgraded shows
        // neither and this section collapses to the divider above.
        AppDatabase.lastRescue?.let { rescue ->
            Text(
                text = "Carried forward on this launch: $rescue",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (setAside.isNotEmpty()) {

            Text("Set aside by an upgrade")

            Text(
                text = "The database as it was before a schema change, " +
                        "whole. Rides that couldn't be carried forward are " +
                        "still in here. It can't be restored into this " +
                        "version - share it out and keep it.",
                style = MaterialTheme.typography.bodySmall
            )

            setAside.forEach { file ->
                OutlinedButton(
                    onClick = {
                        DbBackupUtils.shareFile(context, file, "Share ${file.name}")
                    }
                ) {
                    Text("${file.name} — ${file.length() / 1024} kB")
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider()

        Button(onClick = { eventLog = EventLog.read(context) }) {
            Text(if (eventLog == null) "Show event log" else "Refresh event log")
        }

        if (eventLog != null) {

            OutlinedButton(
                onClick = {
                    EventLog.clear(context)
                    eventLog = EventLog.read(context)
                    Toast.makeText(context, "Event log cleared", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Clear event log")
            }

            // Newest last, and lines are long - let it scroll both ways
            // rather than wrapping GPS coordinates into porridge.
            Text(
                text = eventLog.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
        }
    }

    if (confirmRestore) {

        AlertDialog(

            onDismissRequest = { confirmRestore = false },

            title = { Text("Restore from backup?") },

            text = {
                Text(
                    "This replaces every ride, place, and stop with the " +
                            "backup" +
                            (backupTime?.let { " from ${stamp.format(Date(it))}" } ?: "") +
                            ".\n\nWhat is on the phone now is not kept. Back " +
                            "it up first if you want it.\n\nThe app closes and " +
                            "opens again to finish."
                )
            },

            confirmButton = {

                Button(onClick = {

                    confirmRestore = false

                    // Staged, not swapped. The restart below is how the
                    // user gets to a process that will apply it, but the
                    // restore no longer depends on the restart happening -
                    // see PendingRestore.
                    if (DbBackupUtils.restore(context)) {
                        AppRestart.restart(context)
                    } else {
                        Toast.makeText(
                            context,
                            "No backup found",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) {
                    Text("Restore")
                }
            },

            dismissButton = {
                OutlinedButton(onClick = { confirmRestore = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmRepair) {

        AlertDialog(

            onDismissRequest = { confirmRepair = false },

            title = { Text("Repair place links?") },

            text = {
                Text(
                    "Rides and stops that point at a place which no longer " +
                            "exists are re-linked to the place they fall " +
                            "inside.\n\nDon't run this with an unfinished " +
                            "import. A ride imported before its places are " +
                            "imported looks the same as a broken link, and " +
                            "will be re-linked to the wrong place.\n\nThis " +
                            "cannot be undone."
                )
            },

            confirmButton = {

                Button(onClick = {

                    confirmRepair = false

                    scope.launch {
                        val enforcer = AppRepository.getPlaceBoundaryEnforcer(context)
                        enforcer.enforceBoundaryForAll()
                        enforcer.reattachOrphans()
                        Toast.makeText(
                            context,
                            "Place links repaired",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("Repair")
                }
            },

            dismissButton = {
                OutlinedButton(onClick = { confirmRepair = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
