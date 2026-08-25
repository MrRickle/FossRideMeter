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

import org.fossridemeter.app.model.DistanceUnit
import org.fossridemeter.app.model.MeasurementSystem
import org.fossridemeter.app.model.TimeUnit
import java.util.Locale

private const val METERS_PER_MILE = 1609.344
private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_KILOMETER = 1000.0

val MeasurementSystem.distanceUnit: DistanceUnit
    get() = when (this) {
        MeasurementSystem.US -> DistanceUnit.MILES
        MeasurementSystem.SI -> DistanceUnit.KILOMETERS
    }

val MeasurementSystem.gpsAccuracyUnit: DistanceUnit
    get() = when (this) {
        MeasurementSystem.US -> DistanceUnit.FEET
        MeasurementSystem.SI -> DistanceUnit.METERS
    }

fun displayDistance(
    meters: Double,
    unit: DistanceUnit
): String =
    when (unit) {
        DistanceUnit.MILES ->
            "%.2f mi".format(Locale.US, meters / METERS_PER_MILE)
        DistanceUnit.FEET ->
            "%.2f ft".format(Locale.US,  meters / METERS_PER_FOOT)

        DistanceUnit.KILOMETERS ->
            "%.2f km".format(Locale.US, meters / METERS_PER_KILOMETER)

        DistanceUnit.METERS ->
            "%.2f m".format(Locale.US, meters)
    }

fun distanceToUnit(
    meters: Double,
    unit: DistanceUnit
): Double =
    when (unit) {
        DistanceUnit.MILES ->
            meters / METERS_PER_MILE

        DistanceUnit.FEET ->
            meters / METERS_PER_FOOT

        DistanceUnit.KILOMETERS ->
            meters / METERS_PER_KILOMETER

        DistanceUnit.METERS ->
            meters
    }

fun unitToDistance(
    value: Double,
    unit: DistanceUnit
): Double =
    when (unit) {
        DistanceUnit.MILES ->
            value * METERS_PER_MILE

        DistanceUnit.FEET ->
            value * METERS_PER_FOOT

        DistanceUnit.KILOMETERS ->
            value * METERS_PER_KILOMETER

        DistanceUnit.METERS ->
            value
    }

fun displaySpeedRateAbbreviation(
    unit: DistanceUnit,
    timeUnit: TimeUnit
): String =
    when (unit) {
        DistanceUnit.MILES ->
            when (timeUnit) {
                TimeUnit.SECONDS -> "mi/s"
                TimeUnit.MINUTES -> "mi/min"
                TimeUnit.HOURS -> "mph"
            }

        DistanceUnit.FEET ->
            when (timeUnit) {
                TimeUnit.SECONDS -> "ft/s"
                TimeUnit.MINUTES -> "ft/min"
                TimeUnit.HOURS -> "ft/h"
            }


        DistanceUnit.KILOMETERS ->
            when (timeUnit) {
                TimeUnit.SECONDS -> "km/s"
                TimeUnit.MINUTES -> "km/min"
                TimeUnit.HOURS -> "km/h"
            }

        DistanceUnit.METERS ->
            when (timeUnit) {
                TimeUnit.SECONDS -> "m/s"
                TimeUnit.MINUTES -> "m/min"
                TimeUnit.HOURS -> "m/h"
            }
    }

fun displayDistanceRate(
    perMeterRate: Double,               // dollars per meter
    unit: DistanceUnit
): String =
    when (unit) {
        DistanceUnit.MILES -> {
            val rate = String.format(Locale.US, "%.2f", perMeterRate * METERS_PER_MILE)
            "$$rate / mi"
        }
        DistanceUnit.FEET -> {
            val rate = String.format(Locale.US,"%.2f",perMeterRate * METERS_PER_FOOT)
            "$$rate / ft"
        }
        DistanceUnit.KILOMETERS -> {
            val rate = String.format(Locale.US,"%.2f",perMeterRate * METERS_PER_KILOMETER)
            "$$rate / km"
        }
        DistanceUnit.METERS -> {
            val rate = String.format(Locale.US, "%.2f", perMeterRate)
            "$$rate / m"
        }
    }

fun displaySpeed(
    metersPerSecond: Double,
    unit: DistanceUnit
): String =
    when (unit) {
        DistanceUnit.MILES -> "%.1f mph".format(Locale.US, metersPerSecond * 2.236936f)
        DistanceUnit.FEET -> "%.1f ft/s".format(Locale.US, metersPerSecond * 3.28084f)
        DistanceUnit.METERS ->"%.1f m/s".format(Locale.US, metersPerSecond)
        DistanceUnit.KILOMETERS -> "%.1f km/h".format(Locale.US, metersPerSecond * 3.6f)
    }


fun displayElapsedTime(
    seconds: Long
): String {

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return "%02d:%02d:%02d".format(
        hours,
        minutes,
        secs
    )
}

fun displayAmount(
    amount: Double,
): String {
    val amt = String.format(Locale.US, "%.2f", amount)
    return "$$amt"
}

fun DistanceUnit.displayName(): String =
    when (this) {
        DistanceUnit.FEET -> "Feet"
        DistanceUnit.MILES -> "Miles"
        DistanceUnit.METERS -> "Meters"
        DistanceUnit.KILOMETERS -> "Kilometers"
    }

fun DistanceUnit.displayPerUnitName(): String =
    when (this) {
        DistanceUnit.FEET -> "Foot"
        DistanceUnit.MILES -> "Mile"
        DistanceUnit.METERS -> "Meter"
        DistanceUnit.KILOMETERS -> "Kilometer"
    }

fun DistanceUnit.abbreviation(): String =
    when (this) {
        DistanceUnit.FEET -> "ft"
        DistanceUnit.MILES -> "mi"
        DistanceUnit.METERS -> "m"
        DistanceUnit.KILOMETERS -> "km"
    }

// Conversion factor: how many meters are in one of this unit
fun DistanceUnit.metersPerUnit(): Double =
    when (this) {
        DistanceUnit.MILES -> METERS_PER_MILE
        DistanceUnit.FEET -> METERS_PER_FOOT
        DistanceUnit.KILOMETERS -> METERS_PER_KILOMETER
        DistanceUnit.METERS -> 1.0
    }

// Convert a stored per-meter rate → rate in the selected unit
fun DistanceUnit.toDisplayRate(perMeterRate: Double): Double =
    perMeterRate * metersPerUnit()

// Convert a user-entered rate in the selected unit → per-meter rate
fun DistanceUnit.toPerMeterRate(displayRate: Double): Double =
    displayRate / metersPerUnit()    