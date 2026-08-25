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

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.fossridemeter.app.util.Donations

/**
 * Ways to donate, on the About screen and nowhere else - the app asks for
 * nothing while it is being used.
 *
 * Renders nothing at all when no method is configured (see Donations),
 * so a build with no addresses filled in doesn't advertise a section
 * that can't do anything.
 *
 * Each row does three things, and each is one tap: the row itself hands
 * the handle or address to whatever app claims it - a browser for the
 * two payment services, a wallet for the two crypto schemes - the copy
 * button puts the same string on the clipboard, and the QR button shows
 * it as a code.
 *
 * The three are for three situations. Tapping works when the app that
 * takes the money is on this phone. Copying works when it isn't but the
 * string can be carried somewhere it is. The QR code is for the case
 * neither covers and the one that actually comes up in a cab: the
 * passenger is holding their own phone, and the only thing that crosses
 * between the two is what their camera can see.
 */
@Composable
fun DonateSection(modifier: Modifier = Modifier) {

    val methods = Donations.methods()
    if (methods.isEmpty()) return

    val context = LocalContext.current

    var qrMethod by remember { mutableStateOf<Donations.Method?>(null) }

    qrMethod?.let { method ->
        DonateQrDialog(method = method, onDismiss = { qrMethod = null })
    }

    Column(modifier = modifier.fillMaxWidth()) {

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Support development",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "The app is free and always will be. If it earns you " +
                    "money, you can send some back.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        methods.forEachIndexed { index, method ->

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openDonation(context, method) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Monospaced and never abbreviated: an address is
                    // read back character by character against the
                    // wallet it came from, and a truncated one can't be.
                    Text(
                        text = method.shown,
                        color = tappableValueColor,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                IconButton(onClick = { qrMethod = method }) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = "Show ${method.label} QR code"
                    )
                }

                IconButton(onClick = { copyDonation(context, method) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy ${method.label} address"
                    )
                }
            }

            if (index < methods.size - 1) {
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * One method's address as a QR code, big enough to be scanned off this
 * screen by the camera of a phone held in front of it.
 *
 * The full string is printed under the code as well. It is the same
 * verification the row does - an address read back character by
 * character against the wallet it came from - and it is the fallback
 * when a camera won't focus.
 */
@Composable
private fun DonateQrDialog(
    method: Donations.Method,
    onDismiss: () -> Unit,
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(method.label) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                QrCode(
                    text = method.qr,
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = "${method.label} QR code"
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = method.shown,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    )
}

/**
 * Hands the method's uri to whatever app handles it, and falls back to
 * the clipboard when nothing does - no wallet installed for bitcoin: or
 * lightning:, which is the common case on a phone that only browses.
 *
 * The try/catch is the check. resolveActivity() would be the tidier
 * test, but from Android 11 package visibility makes it return null for
 * apps this one can't see, while startActivity itself still works; a
 * <queries> entry to make it honest would be a manifest declaration per
 * wallet scheme for no gain.
 */
private fun openDonation(context: Context, method: Donations.Method) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, method.uri.toUri()))
    } catch (e: ActivityNotFoundException) {
        copyDonation(
            context,
            method,
            message = "No app for ${method.label}. Copied it instead."
        )
    }
}

private fun copyDonation(
    context: Context,
    method: Donations.Method,
    message: String = "Copied ${method.label}.",
) {

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(method.label, method.shown))

    // Android 13 shows its own copy confirmation, so saying it again
    // would stack two notices for the one action.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
