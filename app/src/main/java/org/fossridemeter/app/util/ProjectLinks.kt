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
package org.fossridemeter.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Where the project lives, and how the app points at it.
 *
 * These are handed to a browser, never fetched. The app holds no
 * `INTERNET` permission - see "Backup is off, and export is the
 * replacement" in `docs/decisions.md` - so it cannot ask GitHub what the
 * latest version is, and adding the permission to find out would trade
 * a claim anyone can verify in the merged manifest for a convenience
 * the browser already provides.
 *
 * What this means in practice: **Check for updates** opens the releases
 * page. It does not tell the user whether an update exists; it takes
 * them to where that is written, next to the version the About screen
 * is already showing them. For a phone that should notice updates on
 * its own, Obtainium watches this same page and needs nothing from
 * here.
 */
object ProjectLinks {

    const val REPO = "https://github.com/MrRickle/FossRideMeter"

    const val LATEST_RELEASE = "$REPO/releases/latest"
}

/**
 * Opens a link in whatever app handles it, or copies it when nothing
 * does.
 *
 * The same shape as the donation links and the maps link: hand the URI
 * over, and if no app claims it put it on the clipboard so the user is
 * left somewhere useful rather than with a dead button. try/catch
 * rather than resolveActivity(), which from Android 11 returns null for
 * apps this one cannot see while startActivity still works.
 */
fun openLink(
    context: Context,
    url: String,
    label: String = "Link",
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {

        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(ClipData.newPlainText(label, url))

        Toast.makeText(
            context,
            "No app to open that. Copied the address instead.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
