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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.data.RadiusConflict
import org.fossridemeter.app.model.DistanceUnit
import org.fossridemeter.app.util.abbreviation
import org.fossridemeter.app.util.distanceToUnit

/**
 * Asks which radius to keep for the imported places that are already on
 * this phone with a different one.
 *
 * Nothing is ticked to start with: a radius decides which rides and
 * stops a place claims, so changing one on the phone that recorded them
 * is something the user opts into, place by place. Dismissing keeps
 * every radius as it is.
 */
@Composable
fun PlaceRadiusConflictDialog(
    conflicts: List<RadiusConflict>,
    unit: DistanceUnit,
    onDone: (useFile: List<RadiusConflict>) -> Unit,
) {

    val useFile = remember(conflicts) { mutableStateListOf<RadiusConflict>() }

    fun radius(meters: Double) = "%.0f %s".format(distanceToUnit(meters, unit), unit.abbreviation())

    AlertDialog(

        onDismissRequest = { onDone(emptyList()) },

        title = {
            Text("Different radius")
        },

        text = {
            Column {
                Text(
                    "These places are already on this phone with a different radius.\n\n" +
                            "Tick the ones that should use the radius from the file."
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(conflicts) { conflict ->
                        val checked = conflict in useFile
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = {
                                    if (it) useFile += conflict else useFile -= conflict
                                }
                            )
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column {
                                Text(conflict.existing.name)
                                Text(
                                    "Here ${radius(conflict.existing.radiusMeters)}, " +
                                            "file ${radius(conflict.incoming.radiusMeters)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },

        confirmButton = {
            TextButton(
                enabled = useFile.isNotEmpty(),
                onClick = { onDone(useFile.toList()) }
            ) {
                Text("Use file's radius")
            }
        },

        dismissButton = {
            TextButton(onClick = { onDone(emptyList()) }) {
                Text("Keep all")
            }
        }
    )
}
