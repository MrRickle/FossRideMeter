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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import org.fossridemeter.app.data.AppDatabase
import org.fossridemeter.app.data.PendingRestore
import java.io.File

/**
 * Raw SQLite-file backup/restore for debug use only.
 *
 * This is deliberately schema-agnostic (it just copies the .db file) so it
 * keeps working as tables are added (location names, stops, etc.) without
 * needing updates. It is NOT a real export/import feature: no versioning,
 * no validation of what's inside, and a file only the app version that
 * wrote it can be relied on to read back. The user-facing export is the
 * JSON one in PlacesBackup/RidesBackup.
 *
 * It ships in release builds, on the Advanced screen: it is the only way
 * to get a database off the phone whole, and the only way to put one
 * back after an upgrade or an experiment went wrong.
 */
object DbBackupUtils {

    private const val DB_NAME = "rides.db"
    private const val BACKUP_NAME = "rides_backup.db"

    /**
     * Copies the live Room database file to app-specific external storage
     * (Android/data/<package>/files/ on the device — no permissions needed,
     * but not visible to other apps). Checkpoints WAL into the main file
     * first so the copy isn't missing recent writes.
     */
    fun backup(context: Context): File {
        val db = AppDatabase.getInstance(context)
        // PRAGMA wal_checkpoint returns a result row (checkpoint status, log
        // frames, checkpointed frames), so it has to go through query() —
        // execSQL() only accepts statements that don't return rows and throws
        // "Queries can be performed using SQLiteDatabase query or rawQuery
        // methods only" otherwise.
        db.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
            .use { /* cursor discarded, we only need the checkpoint to run */ }

        val dbFile = context.getDatabasePath(DB_NAME)
        val backupDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files dir unavailable")
        val backupFile = File(backupDir, BACKUP_NAME)

        dbFile.copyTo(backupFile, overwrite = true)
        return backupFile
    }

    /**
     * Asks for the last backup made by [backup] to be put back.
     *
     * This does not swap the file. It used to, and closing Room's
     * instance was not enough: AppRepository went on handing out DAOs
     * built on the closed handle and every screen went on collecting
     * Flows from it, so the app carried on running against a database
     * that was no longer there. See PendingRestore, which does the swap
     * at the start of the next process instead - and AppRestart, which
     * is how the caller gets there.
     */
    fun restore(context: Context): Boolean {
        val backupFile = backupFile(context) ?: return false
        return PendingRestore.stage(context, backupFile)
    }

    fun hasBackup(context: Context): Boolean = backupFile(context)?.exists() == true

    /** When [backup] last wrote, so a restore can say what it is about to undo. */
    fun backupTime(context: Context): Long? =
        backupFile(context)?.takeIf { it.exists() }?.lastModified()

    private fun backupFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, BACKUP_NAME) }

    /**
     * Launches a share sheet so the backup file can be pulled off-device
     * (email, Drive, etc.) without adb. Requires a FileProvider entry in
     * the manifest — see file_paths.xml / AndroidManifest.xml notes.
     */
    fun shareBackup(context: Context) {
        val backupDir = context.getExternalFilesDir(null) ?: return
        shareFile(context, File(backupDir, BACKUP_NAME), "Share rides.db backup")
    }

    /**
     * The same share sheet for any file the FileProvider covers - the
     * manual backup above, or a database SchemaRescue set aside during an
     * upgrade. Silently does nothing for a file that isn't there, since
     * both callers list the files they offer.
     */
    fun shareFile(context: Context, file: File, title: String) {

        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }
}
