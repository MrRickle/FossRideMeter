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

import kotlinx.serialization.Serializable

/**
 * How PlaceWatcher gets each fix while watching for a place crossing.
 *
 * [GPS] asks for a real fix every sweep. Accurate enough to decide
 * containment outright, so a crossing is noticed on the sweep it happens
 * and confirmed on the next.
 *
 * [TIERED] asks the cheapest provider first and spends GPS only when that
 * fix cannot settle the question. It costs far less while parked - a
 * confirmed, stationary vehicle uses no GPS at all - but a coarse fix is
 * routinely too imprecise to resolve a place at all, so a crossing may
 * take several sweeps and an escalation before it is believed. That delay
 * is the price, and it is not small.
 */
@Serializable
enum class WatchAccuracy(
    val displayName: String
) {
    GPS("GPS every check (faster)"),
    TIERED("Cheapest first (less battery, slower)"),
}
