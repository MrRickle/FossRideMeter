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

/**
 * Where donations can go, and the only place any of it is written down.
 *
 * Every value is blank until it is filled in here, and a blank one is
 * dropped from [methods] rather than rendered as an empty row - so the
 * About screen simply has no donation section until there is somewhere
 * for the money to actually go. Nothing else in the app has to know
 * which methods exist.
 *
 * Fill in the bare handle or address, not a URL: [methods] builds the
 * link. A wrong address here is money sent to a stranger, so paste them
 * from the wallet or account itself and check them on the About screen
 * afterwards - it shows the full string precisely so it can be read back
 * against the source.
 */
object Donations {

    /** paypal.me handle, e.g. "rickhallock". */
    const val PAYPAL_ME = "RideMeter"

    /** Venmo username without the @, e.g. "Rick-Hallock". */
    const val VENMO = ""

    /** On-chain Bitcoin receiving address. */
    const val BITCOIN = "bc1pk9h7cr20r47gcl8vv46m5l29dhtx03cp94znsjlfsjr3r8lt00csx860v9"

    /**
     * Lightning address (user@domain) or a static LNURL. Both are opened
     * with the lightning: scheme, which every wallet that handles either
     * registers for.
     */
    const val LIGHTNING = "lno1pgqppmsrse80qf0aara4slvcjxrvu6j2rp5ftmjy4yntlsmsutpkvkt6878s88uz6dprasjpa4jsxhskujwts0fj62vzklpz047svr06vndd3l9rqgp3rzu6jsj96m8ayuse3zkscpsg4rdxqpyxv73e2dmyd5msemq4j4gqxdd5n27w6yql9lglzdd6uav8qgy4csadxmrnla0m6dt5zjnlvzvk4wj65cjmqu23vlsjsv2u2g73f5p0hqksxz2jt5z9c0h6n486kef8qjhxvv5tf0q5fwtukyp9ysfy6lrmekxeqqew0l883vnpr666nf07z3f08q8yfsx6eamd854tc9mprzmz7uzvdzjprnhylkrn45v4l6cqanzx08pe950q"

    /**
     * [shown] is what the user reads and copies; [uri] is what a tap
     * opens; [qr] is what the QR code encodes. For the two wallets
     * [shown] and [uri] are the same string - an address is meant to be
     * read back and verified against the wallet it came from, so it is
     * never abbreviated.
     *
     * [qr] is separate from [uri] only because of case. A QR code stores
     * uppercase A-Z, 0-9 and a few symbols in alphanumeric mode at under
     * two thirds the size of the same text in byte mode, which is the
     * difference between a code a phone camera reads at arm's length and
     * one it has to hunt for. Bech32 addresses and URI schemes are both
     * case-insensitive, so the two crypto rails can be uppercased for
     * that saving and every wallet still reads them - it is what wallets
     * print on their own receive screens. The two payment links cannot:
     * an http URL's path is case-sensitive, and "PAYPAL.ME/RIDEMETER"
     * is not a page. They stay as they are and encode as bytes, which
     * they are short enough to afford.
     */
    data class Method(
        val label: String,
        val shown: String,
        val uri: String,
        val qr: String = uri,
    )

    fun methods(): List<Method> = buildList {

        if (PAYPAL_ME.isNotBlank()) {
            add(Method("PayPal", "paypal.me/$PAYPAL_ME", "https://paypal.me/$PAYPAL_ME"))
        }

        if (VENMO.isNotBlank()) {
            add(Method("Venmo", "@$VENMO", "https://venmo.com/u/$VENMO"))
        }

        if (BITCOIN.isNotBlank()) {
            add(
                Method(
                    label = "Bitcoin",
                    shown = BITCOIN,
                    uri = "bitcoin:$BITCOIN",
                    qr = "bitcoin:$BITCOIN".uppercase(),
                )
            )
        }

        if (LIGHTNING.isNotBlank()) {
            add(
                Method(
                    label = "Lightning",
                    shown = LIGHTNING,
                    uri = "lightning:$LIGHTNING",
                    qr = "lightning:$LIGHTNING".uppercase(),
                )
            )
        }
    }
}
