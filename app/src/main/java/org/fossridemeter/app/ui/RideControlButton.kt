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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.ui.theme.StatusColors

@Composable
fun RideControlButton(
    status: RideStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {

    val onClick: () -> Unit =
        when (status) {

            RideStatus.READY ->
                onStart

            RideStatus.RUNNING ->
                onPause

            RideStatus.PAUSED ->
                onResume
        }

    // The color is the status — see theme/StatusColors.kt.
    val statusColor =
        when (status) {

            RideStatus.READY ->
                StatusColors.idle

            RideStatus.RUNNING ->
                StatusColors.go

            RideStatus.PAUSED ->
                StatusColors.caution
        }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = statusColor.container,
            contentColor = statusColor.onContainer
        ),
        modifier = modifier
            .width(220.dp)
            .height(70.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.buttonText,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
            )
        }
    }
}
