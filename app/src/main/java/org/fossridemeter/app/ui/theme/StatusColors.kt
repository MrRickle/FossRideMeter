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
package org.fossridemeter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Semantic status colors — go, caution, idle — plus [StatusColors.stop], which
 * defers to the color scheme.
 *
 * Material 3 defines exactly one semantic role, `error`; there is no `success`
 * or `warning`, and the spec's answer is that an app supplies its own extended
 * set. This is ours. They are fixed values rather than scheme-derived so a
 * status means the same thing on every device: the theme is dynamic on
 * Android 12+, so a wallpaper-derived primary could land on any hue at all.
 *
 * Use these only where a color *is* the information. Anything that is merely
 * decorative should still come from MaterialTheme.colorScheme so it follows the
 * user's wallpaper.
 */

// Each pair is (light theme, dark theme). The dark variants are lightened so
// they stay legible against a dark surface.
private val GoGreen = Color(0xFF2E7D32)
private val GoGreenDark = Color(0xFF7CC47F)
private val CautionYellow = Color(0xFFF2B705)
private val CautionYellowDark = Color(0xFFFFD24A)
private val IdleBlue = Color(0xFF1565C0)
private val IdleBlueDark = Color(0xFF79B4EC)

// Yellow is the one hue that can't do double duty: as text on a light surface
// CautionYellow is far short of the 4.5:1 contrast minimum, so text gets a
// darkened amber instead. The other hues clear it as they are.
private val CautionAmberText = Color(0xFF8A6100)

/**
 * One status in both the forms the UI needs it: as a filled [container] with
 * [onContainer] drawn on top, or as [content] — text or an icon on a plain
 * surface.
 */
@Immutable
data class StatusColor(
    val container: Color,
    val onContainer: Color,
    val content: Color,
)

object StatusColors {

    /** Running, healthy, good to go. */
    val go: StatusColor
        @Composable get() =
            if (isSystemInDarkTheme())
                StatusColor(GoGreenDark, Color.Black, GoGreenDark)
            else
                StatusColor(GoGreen, Color.White, GoGreen)

    /** Degraded or held — working, but not as intended. */
    val caution: StatusColor
        @Composable get() =
            if (isSystemInDarkTheme())
                StatusColor(CautionYellowDark, Color.Black, CautionYellowDark)
            else
                StatusColor(CautionYellow, Color.Black, CautionAmberText)

    /** At rest, or waiting on something. Nothing is wrong. */
    val idle: StatusColor
        @Composable get() =
            if (isSystemInDarkTheme())
                StatusColor(IdleBlueDark, Color.Black, IdleBlueDark)
            else
                StatusColor(IdleBlue, Color.White, IdleBlue)

    /** Off or failed. The one status Material 3 already has a role for. */
    val stop: StatusColor
        @Composable get() =
            MaterialTheme.colorScheme.let {
                StatusColor(it.errorContainer, it.onErrorContainer, it.error)
            }
}
