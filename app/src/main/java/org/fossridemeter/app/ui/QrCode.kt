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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/**
 * A QR code, drawn as squares rather than rasterised into a bitmap.
 *
 * The code is always dark on white, in both themes. A scanner can be
 * told which polarity to expect, but a phone camera pointed at another
 * phone's screen is guessing, and a light-on-dark code is the one it
 * guesses wrong. The white [QUIET_ZONE] border around it is part of the
 * code, not padding: without it a scanner cannot find the edge.
 *
 * Returns nothing if the payload is too long to encode - 4296 characters
 * of alphanumeric or 2953 of bytes is the ceiling, far above any address
 * this app carries, but a caller shouldn't have to know that.
 */

/** Module-widths of white kept around the code. Four is the spec minimum. */
private const val QUIET_ZONE = 4

@Composable
fun QrCode(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {

    // Encoding walks the whole payload and allocates the matrix, so it is
    // keyed on the text rather than repeated on every recomposition.
    val matrix = remember(text) { encodeQr(text) } ?: return

    val size = matrix.width

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription?.let { this.contentDescription = it } }
    ) {

        drawRect(color = Color.White, size = this.size)

        // Floored so a module is a whole number of pixels: at these
        // module counts a fractional width leaves seams between the
        // squares that a camera reads as breaks in the pattern. The
        // rounding loss is centred instead of left at one edge.
        val module = floor(this.size.minDimension / (size + 2 * QUIET_ZONE))
        if (module < 1f) return@Canvas

        val drawn = module * (size + 2 * QUIET_ZONE)
        val originX = (this.size.width - drawn) / 2f + module * QUIET_ZONE
        val originY = (this.size.height - drawn) / 2f + module * QUIET_ZONE

        for (y in 0 until size) {
            for (x in 0 until size) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(originX + x * module, originY + y * module),
                        size = Size(module, module),
                    )
                }
            }
        }
    }
}

/**
 * The raw module grid, one bit per module and no quiet zone - this draws
 * its own above.
 *
 * Asking for a 1x1 pixel image is how the writer is told to give back
 * modules instead of a scaled bitmap: it never scales below one pixel
 * per module, so the result is exactly the code's own size. Error
 * correction M recovers about 15% of a damaged code, which is the level
 * wallets use for receive screens - L would be smaller, but a code read
 * off a screen at an angle, or off a photo of one, has damage to spare.
 */
private fun encodeQr(text: String): BitMatrix? =
    try {
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
            ),
        )
    } catch (e: WriterException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
