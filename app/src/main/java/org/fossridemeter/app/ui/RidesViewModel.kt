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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideRecord
import org.fossridemeter.app.model.Stop
import android.util.Log
import org.fossridemeter.app.data.AppRepository
import org.fossridemeter.app.data.exportRidesJson
import org.fossridemeter.app.data.parseRidesJson

class RidesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        AppRepository.get(application)

    private val placeDao =
        AppRepository.getPlaceDao(application)

    private val stopDao =
        AppRepository.getStopDao(application)

    private val placeLocationRecalculator =
        AppRepository.getPlaceLocationRecalculator(application)

    // Only used for the bulk export/import snapshot below - the regular
    // reactive ride list above still goes through the RideRepository
    // interface (repository.rides), same as before.
    private val rideDao =
        AppRepository.getRideDao(application)

    val selection = SelectionState()

    private val _rides =
        MutableStateFlow<List<RideRecord>>(emptyList())

    val rides: StateFlow<List<RideRecord>> =
        _rides.asStateFlow()

    private val _places =
        MutableStateFlow<Map<String, Place>>(emptyMap())

    val places: StateFlow<Map<String, Place>> =
        _places.asStateFlow()

    private val _stopsByRide =
        MutableStateFlow<Map<String, List<Stop>>>(emptyMap())

    val stopsByRide: StateFlow<Map<String, List<Stop>>> =
        _stopsByRide.asStateFlow()

    init {

        viewModelScope.launch {

            repository.rides.collect {

                Log.d(
                    "RideHistoryViewModel",
                    "Received ${it.size} rides"
                )


                _rides.value =
                    it.sortedByDescending { ride ->
                        ride.startTime
                    }
            }
        }

        viewModelScope.launch {

            placeDao.getAll().collect { list ->

                _places.value =
                    list.associateBy { place -> place.id }
            }
        }

        viewModelScope.launch {

            stopDao.getAll().collect { list ->

                _stopsByRide.value =
                    list.groupBy { stop -> stop.rideId }
            }
        }
    }

    fun updateRide(
        ride: RideRecord
    ) {
        viewModelScope.launch {
            repository.updateRide(ride)
        }
    }

    fun deleteRide(
        rideId: String
    ) {
        viewModelScope.launch {
            deleteRidesAndRecompute(listOf(rideId))
        }
    }

    /** Deletes every selected ride, then leaves selection mode. */
    fun deleteSelected() {
        val ids = selection.selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            deleteRidesAndRecompute(ids)
            selection.clear()
        }
    }

    /**
     * A deleted ride takes its start point, end point and stops out of
     * whatever places they were linked to, and those places' locations
     * are averages of exactly those points - so every place the deletion
     * touched has to be averaged again without them, the same way
     * RideMeter.cancel() does it.
     *
     * The place ids are collected *before* the rows go, and the recompute
     * runs *after*, because the recalculator reads what is in the
     * database rather than being told what changed.
     */
    private suspend fun deleteRidesAndRecompute(rideIds: List<String>) {

        val touchedPlaceIds = mutableSetOf<String>()

        for (rideId in rideIds) {
            _rides.value.firstOrNull { it.id == rideId }?.let { ride ->
                ride.startPlaceId?.let { touchedPlaceIds.add(it) }
                ride.endPlaceId?.let { touchedPlaceIds.add(it) }
            }
            _stopsByRide.value[rideId].orEmpty().forEach { stop ->
                stop.placeId?.let { touchedPlaceIds.add(it) }
            }
            // Stop.rideId is ForeignKey.CASCADE, so the stops go with it.
            repository.deleteRide(rideId)
        }

        for (placeId in touchedPlaceIds) {
            placeLocationRecalculator.recompute(placeId)
        }
    }

    /** Native backup format - every ride currently shown, each bundled
     * with its own stops. */
    fun exportJson(): String {
        return exportRidesJson(rides.value, stopsByRide.value)
    }

    /**
     * The same format restricted to the current selection, so a subset of
     * the history can be handed over without exporting all of it. Read at
     * write time rather than when the file picker opens, which is why the
     * selection has to still be live - leaving selection mode mid-export
     * would produce an empty file.
     */
    fun exportSelectedJson(): String {
        val ids = selection.selected.value
        return exportRidesJson(
            rides.value.filter { it.id in ids },
            stopsByRide.value,
        )
    }

    /**
     * Rides are only ever exported/imported in our own JSON format (no
     * CSV/GPX equivalent - a ride isn't a point, it's a whole trip with
     * rates, timing, and linked stops). Re-importing a backup that
     * includes a ride/stop id already present overwrites that row rather
     * than duplicating it.
     */
    suspend fun importJson(text: String): Int {
        val entries = parseRidesJson(text)

        // The rows are written on viewModelScope, so navigating away
        // mid-import can't leave half a file imported, but the caller
        // waits for it: the toast that follows is a claim that the rides
        // are in the database, and saying so before they are is how a
        // silent failure gets reported as a success.
        viewModelScope.launch {
            for (entry in entries) {
                rideDao.upsert(entry.ride)
                for (stop in entry.stops) {
                    stopDao.upsert(stop)
                }
            }
        }.join()

        return entries.size
    }
}
