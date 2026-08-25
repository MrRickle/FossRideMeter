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
package org.fossridemeter.app.service

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SystemTimeProvider : TimeProvider {

    private val _elapsedSeconds =
        MutableStateFlow(0L)

    override val elapsedSeconds =
        _elapsedSeconds.asStateFlow()

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private var job: Job? = null

    private var accumulatedMillis = 0L
    private var startMillis = 0L

    override fun start() {

        if (job != null) return

        startMillis = SystemClock.elapsedRealtime()

        job = scope.launch {

            while (isActive) {

                val elapsed =
                    accumulatedMillis +
                    (SystemClock.elapsedRealtime() - startMillis)

                _elapsedSeconds.value = elapsed / 1000

                delay(500.milliseconds)
            }
        }
    }

    override fun stop() {

        job?.cancel()
        job = null

        accumulatedMillis +=
            SystemClock.elapsedRealtime() - startMillis
    }

    override fun reset() {

        stop()

        accumulatedMillis = 0L
        startMillis = 0L

        _elapsedSeconds.value = 0L
    }
}