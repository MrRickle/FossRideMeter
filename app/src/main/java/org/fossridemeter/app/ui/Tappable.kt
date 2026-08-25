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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The colour of a value that leads somewhere.
 *
 * Rows of label-and-value read as a printout: the ones that open an
 * editor or a dialog looked exactly like the ones that only report a
 * number, and nothing on screen said which was which. A user found
 * them by tapping around.
 *
 * From the colour scheme rather than from theme/StatusColors.kt, and
 * that distinction is deliberate: a status colour *is* the information
 * and so is fixed on every device, whereas this is decoration and should
 * follow the user's wallpaper like the rest of the app's chrome.
 */
val tappableValueColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary
