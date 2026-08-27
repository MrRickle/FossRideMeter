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
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import java.util.Locale

/**
 * Opens a point in whatever maps app the phone has.
 *
 * `geo:` is a platform URI scheme (RFC 5870), not a Google one, and
 * handling it is how a maps app gets itself into the chooser at all -
 * Google Maps, Organic Maps, OsmAnd, Magic Earth, HERE and the rest all
 * take it. So this ties the app to no particular map, embeds no maps
 * SDK, needs no API key, and adds no permission: the intent is handed
 * over and the other app does the network work. FossRideMeter still has
 * no INTERNET permission of its own.
 *
 * The URI carries the coordinates twice on purpose. The pair after
 * `geo:` positions the map; the `q=` is what makes apps drop a **pin**.
 * Without it Google Maps centres the area and marks nothing, which is
 * useless for the thing this is for - checking whether a place sits on
 * the right side of the street.
 *
 * The label is a courtesy: apps that support it title the pin, apps that
 * don't ignore it.
 */
fun openInMaps(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "",
) {
    // Locale.US or a comma-decimal locale writes "geo:37,7749,-122,4194",
    // which is not a coordinate pair in any reading.
    val coordinates = "%.6f,%.6f".format(Locale.US, latitude, longitude)

    val query = label.trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "$coordinates(${Uri.encode(it)})" }
        ?: coordinates

    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$query".toUri())
        )
    } catch (e: ActivityNotFoundException) {
        // No maps app installed. Falling back to the clipboard leaves the
        // user exactly where they were before this button existed:
        // paste it wherever they normally look coordinates up. Same
        // reasoning as the donation links, which cannot assume a wallet.
        //
        // try/catch rather than resolveActivity() because from Android 11
        // package visibility makes that return null for apps this one
        // cannot see, while startActivity still works.
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Place location", coordinates)
        )

        Toast.makeText(
            context,
            "No maps app to open that. Copied the location instead.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
