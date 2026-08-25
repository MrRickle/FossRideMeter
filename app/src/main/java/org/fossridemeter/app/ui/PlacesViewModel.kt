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
import org.fossridemeter.app.data.AppRepository
import org.fossridemeter.app.data.PlaceBoundaryEnforcer
import org.fossridemeter.app.data.exportPlacesJson
import org.fossridemeter.app.data.parsePlacesAuto
import org.fossridemeter.app.model.Place

class PlacesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val placeDao =
        AppRepository.getPlaceDao(application)

    private val boundaryEnforcer: PlaceBoundaryEnforcer =
        AppRepository.getPlaceBoundaryEnforcer(application)

    val selection = SelectionState()

    private val _places =
        MutableStateFlow<List<Place>>(emptyList())

    val places: StateFlow<List<Place>> =
        _places.asStateFlow()

    init {
        viewModelScope.launch {
            placeDao.getAll().collect {
                _places.value = it
            }
        }
    }

    /**
     * Saving through the editor always flips isNamed = true - it's purely
     * a display flag ("has a real name"), doesn't affect matching or
     * averaging.
     *
     * An upsert, not an update, because the same editor also creates: a
     * place named from a stop in the stops list arrives here as a row
     * that isn't in the database yet (see placeAt). Everything after that
     * is identical for a new place and an edited one, which is the point
     * - a place drawn over a stop has to claim that stop exactly the way
     * a widened place claims what it now covers.
     *
     * locationLocked only flips true if latitude/longitude actually
     * changed from what's currently stored - i.e. the user edited the
     * location field itself (typed or pasted new coordinates), not just
     * the name, radius, or auto flags. A new place is never locked by
     * arriving: its coordinates came from a point that was recorded, not
     * from anyone typing, so it keeps converging like any other. A place
     * can be named and still keep converging via
     * PlaceLocationRecalculator until its location is actually touched.
     * Once locked it stays locked; there's currently no UI to unlock it
     * again.
     *
     * After saving, PlaceBoundaryEnforcer squares the place with the
     * points around it both ways: anything linked to it that no longer
     * falls within the (possibly changed) location/radius is detached and
     * re-resolved, and the unnamed placeholders that now sit *inside* it
     * are absorbed, so naming an area relabels the stops already recorded
     * in it.
     */
    fun updatePlace(updated: Place) {
        viewModelScope.launch {
            val current = placeDao.getById(updated.id)

            val locationChanged = current != null &&
                (current.latitude != updated.latitude || current.longitude != updated.longitude)

            placeDao.upsert(
                updated.copy(
                    isNamed = true,
                    locationLocked = updated.locationLocked || locationChanged,
                )
            )
            boundaryEnforcer.enforceBoundary(updated.id)
        }
    }

    /**
     * Deletes every selected place, then leaves selection mode.
     *
     * There is deliberately no foreign key cascading a place deletion
     * into ride history, because losing rides would be a far worse
     * outcome than losing a label on them. Instead the rides and stops
     * that pointed at the deleted places are re-resolved from the points
     * they recorded, the same way an edited place's boundary re-resolves
     * them - a deleted place comes back as a geohash placeholder rather
     * than as a blank column or a `?` in the stops list.
     *
     * The delete has to land first: PlaceResolver matches against what is
     * in the database, and would hand back the very place being removed.
     *
     * A place flagged autoStart/autoSave simply stops being watched, as
     * PlaceWatcher reads the place list reactively.
     */
    fun deleteSelected() {
        val ids = selection.selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            delete(ids)
            selection.clear()
        }
    }

    /**
     * One place, from the editor rather than from a selection - the same
     * gesture that deletes a ride from its detail dialog.
     *
     * It goes through [delete] rather than repeating it, because the
     * re-resolution afterwards is the part that must not be forgotten:
     * a place removed without it leaves every ride and stop that pointed
     * at it holding an id nothing answers to.
     */
    fun deletePlace(id: String) {
        viewModelScope.launch { delete(listOf(id)) }
    }

    /** Delete first, then re-resolve - see [deleteSelected] for why. */
    private suspend fun delete(ids: List<String>) {
        placeDao.deleteByIds(ids)
        boundaryEnforcer.reattachDeleted(ids)
    }

    /** Native backup format - lossless round trip of everything currently
     * shown on screen. */
    fun exportJson(): String {
        return exportPlacesJson(places.value)
    }

    /** The same format restricted to the current selection. */
    fun exportSelectedJson(): String {
        val ids = selection.selected.value
        return exportPlacesJson(places.value.filter { it.id in ids })
    }

    /**
     * Accepts our own JSON backup, a generic CSV (name, lat, lng[,
     * radius]), or a GPX waypoints file (OsmAnd/Organic Maps Favorites,
     * or anything converted from Google Takeout/My Maps) - format is
     * picked from the file extension when there is one, otherwise
     * sniffed from the content itself.
     *
     * Imported places are inserted directly; they aren't yet linked to
     * any ride, so there's nothing to re-resolve here the way
     * updatePlace() does via PlaceBoundaryEnforcer.
     */
    suspend fun importPlaces(text: String, fileName: String?): Int {
        val parsed = parsePlacesAuto(text, fileName)

        // Written on viewModelScope but awaited by the caller - see the
        // same note on RidesViewModel.importJson.
        viewModelScope.launch {
            for (place in parsed) {
                placeDao.upsert(place)
            }
        }.join()

        return parsed.size
    }
}
