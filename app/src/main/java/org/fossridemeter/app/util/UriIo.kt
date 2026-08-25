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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

fun readTextFromUri(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.bufferedReader().readText()
    } ?: throw IOException("Could not open $uri for reading")
}

/**
 * Mode "wt", not the default "w": plain "w" is not required to truncate,
 * and several providers don't, so exporting a short file over a longer
 * one leaves the tail of the old one behind and the result no longer
 * parses. Truncating is what makes an export a replacement.
 */
fun writeTextToUri(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
        stream.bufferedWriter().use { it.write(text) }
    } ?: throw IOException("Could not open $uri for writing")
}

/**
 * Bytes already in a SAF-picked document, or null when the provider
 * won't say.
 *
 * Used to tell "the picker made me a new file" from "the picker handed
 * back one that already has something in it": ACTION_CREATE_DOCUMENT
 * creates an empty document for a fresh name, so anything non-zero here
 * is a file about to be replaced and worth asking about first. Providers
 * that auto-rename to avoid a collision land on the empty side of this,
 * which is right - nothing is being replaced.
 */
fun fileSizeFromUri(context: Context, uri: Uri): Long? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
    return null
}

/** Best-effort display name for a SAF-picked Uri - used to sniff import
 * format by extension. Falls back to the last path segment if the
 * content provider doesn't report a display name. */
fun fileNameFromUri(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    return uri.lastPathSegment
}
