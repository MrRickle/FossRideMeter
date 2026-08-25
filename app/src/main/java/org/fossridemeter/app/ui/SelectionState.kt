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
package org.fossridemeter.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which rows of a table are currently selected, by id.
 *
 * Held by the ViewModel rather than the screen because the contextual top
 * bar - the count, "select all", delete - is drawn by AppNavigation, not
 * by the table. Both need the same set, and the screen is the wrong place
 * for state something outside it has to read.
 *
 * The empty set is not merely "nothing selected", it *is* "not in
 * selection mode": there is no separate boolean to keep in step with it,
 * and clearing the selection is what leaves the mode.
 */
class SelectionState {

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun toggle(id: String) {
        _selected.value = _selected.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAll(ids: Collection<String>) {
        _selected.value = ids.toSet()
    }

    fun clear() {
        _selected.value = emptySet()
    }
}
