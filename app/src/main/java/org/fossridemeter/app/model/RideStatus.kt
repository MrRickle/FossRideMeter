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
package org.fossridemeter.app.model

enum class RideStatus(
    val displayName: String,
    val buttonText: String
) {
    READY(
        "READY",
        "START"
    ),

    RUNNING(
        "RUNNING",
        "PAUSE"
    ),

    // Tracking is stopped but the ride is fully intact: its row, its
    // stops, and its end location are all already in the database, and
    // the providers are still alive. So a paused ride can go either way -
    // resume and keep accumulating, or save and be done. There is no
    // separate "finished" status, because there is nothing a finish step
    // would have to commit that isn't committed already.
    PAUSED(
        "PAUSED",
        "RESUME"
    )
}
