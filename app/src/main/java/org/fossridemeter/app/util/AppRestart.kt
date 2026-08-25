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
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Closes the app and asks the system to open it again.
 *
 * Used after staging a restore, where carrying on in the current process
 * is the thing that must not happen: the database file is about to be
 * replaced under a Room instance, a repository holding its DAOs, and
 * every Flow the screens are collecting.
 *
 * [Intent.makeRestartActivityTask] is the launcher's own recipe - a new
 * task, cleared, rooted at the launch activity - and it is dispatched
 * before this process exits, so the system starts a fresh one. It is
 * deliberately not depended on: PendingRestore lands its work at the
 * *start* of whichever process opens the database next. If the relaunch
 * doesn't happen, the user taps the icon and the restore is already
 * waiting; nothing is half-applied either way.
 *
 * Anything a live ride needs on disk must be written before this is
 * called. It ends the process outright.
 */
object AppRestart {

    fun restart(context: Context) {

        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component

        if (launch != null) {
            context.startActivity(Intent.makeRestartActivityTask(launch))
        }

        exitProcess(0)
    }
}
