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

/**
 * Decides what a GPS fix contributes to a ride's distance.
 *
 * Pulled out of GpsDistanceProvider's callback so it can be tested
 * without a device: this is the arithmetic the whole app exists to
 * produce, and it was wrong in a way nobody could see.
 *
 * ## The bug this was written for
 *
 * Every rejected fix used to advance the anchor. A fix that was too
 * vague, or too slow, or too far therefore did not merely fail to add
 * its own metres - it threw away the distance from the last *counted*
 * fix as well, permanently. Rejecting a fix and moving the anchor to it
 * are opposite decisions, and the old code did both at once.
 *
 * A real two-mile errand with a stop in the middle metered 1.9 miles
 * for 4.4 miles driven. Two filters were eating it: a minimum speed of
 * 8 mph, which rejects every turn, light and parking manoeuvre, and an
 * accuracy requirement of under 10 m, which rejects the whole band the
 * app itself displays as POOR.
 *
 * ## The rule now
 *
 * A fix that cannot be trusted leaves the anchor where it is, so the
 * next fix that *can* be trusted measures from the last trustworthy
 * point. The distance across an untrusted patch is then counted as a
 * straight line - an under-estimate of a curve, but far closer than
 * zero, which is what it used to be.
 */
internal object DistanceFilter {

    /** Everything the decision needs from a Location, and nothing else. */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val speedMps: Float,
        val hasSpeed: Boolean,
        val elapsedMillis: Long,
    )

    sealed interface Verdict {

        /** Add these metres, and move the anchor here. */
        data class Count(val meters: Double) : Verdict

        /**
         * Add nothing, and **leave the anchor alone** - the next usable
         * fix measures from wherever it last was.
         */
        data object Skip : Verdict

        /**
         * Add nothing, but move the anchor here: this fix is fine, the
         * gap to it just isn't measurable as travel.
         */
        data object Reanchor : Verdict
    }

    // The band the app already calls POOR. Anything vaguer than this is
    // not a position, and counting from it invents distance; anything
    // inside it is what a phone in a car actually reports most of the
    // time, and refusing it was most of the shortfall.
    const val MAX_USABLE_ACCURACY_METERS = 30f

    // 134 mph. Not a speed limit - a sanity check on the *implied* speed
    // between two fixes, which is what catches a position that jumped
    // rather than moved. It replaces a flat 100 m cap: once an untrusted
    // patch keeps its anchor, a legitimate gap can be far longer than
    // 100 m, and the flat cap would have thrown away exactly the
    // distance this change is meant to recover.
    private const val MAX_PLAUSIBLE_MPS = 60.0

    fun judge(
        previous: Fix?,
        current: Fix,
        minimumSpeedMps: Double,
        distanceMeters: (Fix, Fix) -> Double,
    ): Verdict {

        // Too vague to measure from or to. Keep the anchor: the next
        // decent fix is measured from the last decent one.
        if (current.accuracyMeters > MAX_USABLE_ACCURACY_METERS) return Verdict.Skip

        if (previous == null) return Verdict.Reanchor

        // Below the minimum speed the vehicle is not travelling, so
        // nothing is added - but the anchor stays, so driving off from
        // here is measured from here rather than from wherever the first
        // fast enough fix happens to land.
        //
        // hasSpeed matters: a provider with no speed reports 0, and
        // gating on that would count nothing at all, forever.
        if (current.hasSpeed && current.speedMps < minimumSpeedMps) return Verdict.Skip

        val meters = distanceMeters(previous, current)
        val seconds = (current.elapsedMillis - previous.elapsedMillis) / 1000.0

        // Out of order or same instant: nothing sensible to measure.
        if (seconds <= 0.0) return Verdict.Skip

        // A jump, not a journey. Move the anchor so the next fix is
        // measured from somewhere real, but don't bill the jump.
        if (meters / seconds > MAX_PLAUSIBLE_MPS) return Verdict.Reanchor

        return Verdict.Count(meters)
    }
}
