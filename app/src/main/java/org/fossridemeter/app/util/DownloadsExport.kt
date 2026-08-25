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

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.IOException

/**
 * Exporting straight to Downloads, with no picker and no permission.
 *
 * From API 29 an app owns what it puts in the Downloads collection: it
 * may insert without asking for anything, and may then update or delete
 * those entries - and only those entries - without a consent dialog.
 * That ownership is the whole feature. It means a repeat export can
 * replace the file the last one wrote instead of asking the system
 * picker for a new name and being handed rides (7).json, and it means
 * the app still cannot touch a file the user made themselves.
 *
 * The pre-29 path is the SAF picker that was always there; there is
 * deliberately no WRITE_EXTERNAL_STORAGE fallback, since adding a
 * storage permission to the manifest for two old versions costs every
 * user something.
 */

/** What [saveTextToDownloads] actually did, for the line shown afterwards. */
data class DownloadsSave(
    /** The name on disk, which is not always the name asked for - see below. */
    val fileName: String,
    /** True when this replaced our own earlier export of the same name. */
    val replaced: Boolean,
)

@RequiresApi(Build.VERSION_CODES.Q)
fun saveTextToDownloads(
    context: Context,
    fileName: String,
    text: String,
    mimeType: String = "application/json",
): DownloadsSave {

    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // Without a storage permission this query can only see rows this app
    // created, so a hit here is necessarily our own previous export and
    // ours to replace. A file the user made that happens to share the
    // name is invisible to us, and stays that way: the insert below
    // lands beside it as rides (1).json rather than over it.
    val existing = resolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
        arrayOf(fileName),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0))
        else null
    }

    val uri = existing ?: resolver.insert(
        collection,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // Hides the row from other apps until there is something in
            // it, so a half-written export is never offered as a file.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    ) ?: throw IOException("Could not create $fileName in Downloads")

    try {
        writeTextToUri(context, uri, text)
    } catch (e: Exception) {
        // A row we just created with nothing in it is litter; a row that
        // was already there is the user's file and stays put.
        if (existing == null) resolver.delete(uri, null, null)
        throw e
    }

    if (existing == null) {
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
    }

    return DownloadsSave(
        // Read back rather than echoed: MediaStore numbers a name that
        // collides with a file we can't see, and the user needs the
        // name that is actually on disk.
        fileName = fileNameFromUri(context, uri) ?: fileName,
        replaced = existing != null,
    )
}

/**
 * Where the system pickers should open: the Downloads folder, since that
 * is where these exports live by default and where the user will look
 * for one to import.
 *
 * Best-effort by nature - `EXTRA_INITIAL_URI` is a hint, and a picker
 * that doesn't recognize the authority simply opens wherever it likes.
 * This is only ever a starting location; it grants nothing, which is why
 * it can name Downloads at all where a tree request could not.
 */
fun downloadsPickerUri(): Uri =
    DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:${Environment.DIRECTORY_DOWNLOADS}"
    )
