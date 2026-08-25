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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import org.fossridemeter.app.R

/**
 * The quick start, in the app.
 *
 * The text is `docs/quickstart.md`, copied into the resources by the
 * build (see copyQuickstart in build.gradle.kts) rather than kept here
 * as a second copy. Edit the markdown; this screen follows.
 *
 * It exists because the README is no use to the person who needs it
 * most: someone who has just sideloaded an APK, been refused a
 * permission by a toggle that looks like it works, and has no idea that
 * Android hides "Allow restricted settings" behind a three-dot menu.
 * That answer has to be reachable from inside the app.
 *
 * The renderer below handles the little of markdown the document
 * actually uses. It is not a markdown library and shouldn't become one:
 * if a document needs more than this, the document is too elaborate for
 * a screen someone reads while standing next to their car.
 */
@Composable
fun HelpScreen() {

    val resources = LocalResources.current

    val blocks = remember(resources) {
        blocksOf(
            resources.openRawResource(R.raw.help)
                .bufferedReader()
                .use { it.readText() }
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        blocks.forEach { block -> HelpBlock(block) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpBlock(block: Block) {

    when (block) {

        Block.Gap ->
            Spacer(Modifier.height(8.dp))

        is Block.Heading ->
            if (block.level == 1) {
                Text(
                    text = plain(block.text),
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = plain(block.text),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
            }

        is Block.Paragraph ->
            if (block.marker.isEmpty()) {
                Text(
                    text = plain(block.text),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // The marker sits in its own column so the item's second
                // line lands under its first rather than under the
                // marker - without that, a wrapped step reads as the
                // next step. The space between items is what stops five
                // of them being one block of text.
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp, bottom = 3.dp)
                ) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = plain(block.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
    }
}

private sealed interface Block {

    data object Gap : Block

    data class Heading(
        val text: String,
        val level: Int
    ) : Block

    /**
     * One run of reflowing text: a paragraph when the marker is empty, a
     * bullet or a numbered step when it isn't. The marker is held apart
     * from the text so it can be laid out in its own column.
     */
    data class Paragraph(
        val marker: String,
        val text: String
    ) : Block
}

/**
 * Groups the document's lines into blocks that reflow.
 *
 * The document is hard-wrapped to fit an editor; a phone is narrower
 * than that and turned sideways it is wider. Rendering one line per
 * source line leaves the ragged column that wrapping twice always
 * produces, so consecutive lines are joined and wrapped once, to the
 * screen - which is also what a markdown renderer does with the same
 * file, so the app and the repository copy read the same.
 *
 * A line ending in two or more spaces is markdown's hard break and
 * ends the run, so a deliberate line break in the source survives.
 */
private fun blocksOf(markdown: String): List<Block> {

    val blocks = mutableListOf<Block>()

    var marker = ""
    val run = StringBuilder()

    fun flush() {
        if (run.isNotEmpty()) {
            blocks += Block.Paragraph(marker, run.toString())
            run.clear()
        }
        marker = ""
    }

    fun start(
        newMarker: String,
        text: String
    ) {
        flush()
        marker = newMarker
        run.append(text)
    }

    markdown.lines().forEach { line ->

        val hardBreak = line.endsWith("  ")
        val text = line.trim()

        when {
            text.isEmpty() -> {
                flush()
                if (blocks.lastOrNull() != Block.Gap) blocks += Block.Gap
            }

            text.startsWith("## ") -> {
                flush()
                blocks += Block.Heading(text.removePrefix("## "), 2)
            }

            text.startsWith("# ") -> {
                flush()
                blocks += Block.Heading(text.removePrefix("# "), 1)
            }

            text.startsWith("* ") || text.startsWith("- ") ->
                start("•", text.drop(2))

            else -> {
                val numbered = NUMBERED.matchEntire(text)

                when {
                    numbered != null ->
                        start(
                            numbered.groupValues[1],
                            numbered.groupValues[2]
                        )

                    // Anything else continues the run above, or opens one.
                    run.isNotEmpty() -> run.append(' ').append(text)

                    else -> start("", text)
                }
            }
        }

        if (hardBreak) flush()
    }

    flush()

    return blocks
}

/** A numbered step, split into its marker and the text after it. */
private val NUMBERED = Regex("(\\d+\\.) (.*)")

private val LINK = Regex("\\[([^]]+)]\\(([^)]+)\\)")

/**
 * Strips the markdown that would otherwise be read aloud as punctuation.
 *
 * Emphasis and code markers simply go: a screen reading `**Location**`
 * or `` `Settings()` `` at somebody is worse than one that reads neither.
 * A link becomes its text followed by its target, because the target is
 * often a URL worth typing and there is nothing here to tap.
 */
private fun plain(markdown: String): String =
    LINK.replace(markdown) { match ->
        val label = match.groupValues[1]
        val target = match.groupValues[2]
        if (label == target) label else "$label ($target)"
    }
        .replace("**", "")
        .replace("`", "")
