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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A table header that sorts its column when tapped.
 *
 * Tapping a new column sorts it ascending; tapping the column already
 * sorted reverses it. Nothing returns the table to its loaded order,
 * deliberately - a third state that most people never find is worse
 * than two they can predict, and the loaded order (newest ride first,
 * places by name) is one tap away on the column that produced it.
 *
 * [key] is an enum per screen rather than the label, so renaming a
 * column cannot quietly unhook its comparator: the `when` that builds
 * the comparator is exhaustive and stops compiling instead.
 */
@Composable
fun <K> SortableHeaderCell(
    label: String,
    width: Dp,
    key: K,
    sortedBy: K?,
    ascending: Boolean,
    onSort: (K) -> Unit,
) {

    val active = key == sortedBy

    Row(
        modifier = Modifier
            .width(width)
            .clickable { onSort(key) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        // Only the sorted column is marked, and with the direction it is
        // sorted in. An indicator on every column would say nothing.
        if (active) {
            Text(
                text = if (ascending) " ▲" else " ▼",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Applies a column sort, or leaves the list in the order it arrived.
 *
 * Null [sortedBy] is the loaded order: newest ride first, places by
 * name. Sorting is done here rather than in the query because several
 * columns aren't columns - a ride's start place is a name in another
 * table, its duration is arithmetic - and a table that could sort only
 * the stored ones would be the more confusing thing.
 */
fun <T, K> List<T>.sortedByColumn(
    sortedBy: K?,
    ascending: Boolean,
    comparator: (K) -> Comparator<T>,
): List<T> {

    val key = sortedBy ?: return this

    return sortedWith(
        comparator(key).let { if (ascending) it else it.reversed() }
    )
}
