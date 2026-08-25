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

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Help screen does with the inline markdown in
 * `docs/quickstart.md`.
 *
 * The screen used to strip `**` rather than render it, so bold looked
 * like nothing had been written at all - which sent the author looking
 * for something that did show up, and what turned up was a heading in
 * the middle of a sentence. The tests below are the promise that the
 * emphasis someone types is the emphasis they get, and that where this
 * renderer decides a marker is not emphasis, a markdown renderer on the
 * web decides the same.
 */
class HelpMarkdownTest {

    private fun boldRanges(markdown: String) =
        styled(markdown).spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }

    @Test
    fun boldIsRenderedBoldRatherThanStripped() {
        val rendered = styled("**Export** before you uninstall.")

        assertEquals("Export before you uninstall.", rendered.text)

        val bold = boldRanges("**Export** before you uninstall.")
        assertEquals(1, bold.size)
        assertEquals(0, bold[0].start)
        assertEquals("Export".length, bold[0].end)
    }

    @Test
    fun boldInTheMiddleOfASentenceCoversOnlyItself() {
        val source = "so **rides will not be restored** by themselves."
        val bold = boldRanges(source)

        assertEquals(1, bold.size)
        assertEquals(
            "rides will not be restored",
            styled(source).text.substring(bold[0].start, bold[0].end)
        )
    }

    /**
     * CommonMark will not open emphasis on a space or close it on one,
     * so `** like this **` is literal asterisks on the web. It has to be
     * literal asterisks here too: a screen that quietly bolded it would
     * tell the author their file is right when the web will show it
     * wrong.
     */
    @Test
    fun aMarkerAgainstASpaceIsNotEmphasis() {
        val source = "** Rides will not be restored automatically. **"

        assertTrue(boldRanges(source).isEmpty())
        assertEquals(source, styled(source).text)
    }

    @Test
    fun anUnclosedMarkerIsLeftAlone() {
        val source = "A rate of 2**3 dollars, and **this never closes"

        assertTrue(boldRanges(source).isEmpty())
        assertEquals(source, styled(source).text)
    }

    @Test
    fun codeSpansKeepTheirTextAndMayHoldSpaces() {
        assertEquals(
            "named like dp3wjy6n until you rename it",
            styled("named like `dp3wjy6n` until you rename it").text
        )

        assertEquals(
            "run add license headers.sh",
            styled("run `add license headers.sh`").text
        )
    }

    /** There is nothing to tap, so the target has to be readable. */
    @Test
    fun aLinkBecomesItsTextFollowedByItsTarget() {
        assertEquals(
            "the releases page (https://example.org/releases)",
            styled("[the releases page](https://example.org/releases)").text
        )
    }

    @Test
    fun aLinkWhoseTextIsItsTargetIsNotRepeated() {
        assertEquals(
            "https://example.org/issues",
            styled("[https://example.org/issues](https://example.org/issues)").text
        )
    }

    @Test
    fun plainTextIsUntouched() {
        val source = "Waiting at a light is not a stop."

        assertEquals(source, styled(source).text)
        assertTrue(styled(source).spanStyles.isEmpty())
    }
}
