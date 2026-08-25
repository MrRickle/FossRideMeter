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
package org.fossridemeter.app.data

import org.fossridemeter.app.model.Place
import org.fossridemeter.app.model.RideLocation
import org.fossridemeter.app.util.DistanceUtil

/**
 * Keeps ride and stop points attached to the right place when the places
 * themselves change - a place is edited, deleted, or newly drawn around
 * points that were filed somewhere else before it existed.
 *
 * Every path ends in the same place: PlaceResolver, the same logic a fresh
 * save would use. Nothing here decides where a point belongs; it only
 * decides which points are worth asking about again.
 */
class PlaceBoundaryEnforcer(
    private val rideDao: RideDao,
    private val stopDao: StopDao,
    private val placeDao: PlaceDao,
    private val placeResolver: PlaceResolver,
    private val placeLocationRecalculator: PlaceLocationRecalculator,
) {

    /**
     * The whole pass for one place, run after it is saved. Three steps,
     * and the order is the point:
     *
     * 1. Points linked to this place that no longer fall inside it are
     *    re-resolved away - the place shrank or moved out from under
     *    them.
     * 2. Points that now fall inside it are re-resolved, wherever they
     *    were filed before. This is what makes a place drawn later apply
     *    to history: name "Home Depot" 400 ft wide and the stops already
     *    recorded in the store answer to it, instead of keeping the
     *    geohashes they were first filed under. It equally makes a small
     *    place drawn *inside* a big one take its stops back, since
     *    PlaceResolver prefers the tighter radius - "Home Depot Windows"
     *    at 80 ft claims what it covers and leaves the rest.
     * 3. Unnamed placeholders inside this place that step 2 emptied are
     *    deleted. A placeholder is bookkeeping the app invented; once
     *    nothing points at it, it is just clutter in the places list.
     *    One holding points of its own - they can sit up to its own
     *    radius outside this place - is left alone.
     *
     * Only unnamed places are ever deleted here, and that is a stronger
     * guarantee than it looks: saving through the place editor always
     * sets isNamed, so an unnamed place is one no user has ever touched.
     * A named place inside another named place (a shop inside a mall)
     * stays, and resolution keeps them apart by radius.
     */
    suspend fun enforceBoundary(placeId: String) {
        val place = placeDao.getById(placeId) ?: return

        val touchedPlaceIds = mutableSetOf(placeId)

        pushOutPointsNowOutside(place, touchedPlaceIds)
        claimPointsNowInside(place, touchedPlaceIds)
        dropEmptiedPlaceholdersInside(place)

        // Every place that gained or lost a point needs its average
        // refreshed - a no-op for whichever of them are locked, and for
        // any that step 3 has since deleted.
        touchedPlaceIds.forEach { placeLocationRecalculator.recompute(it) }
    }

    /**
     * The same pass over every named place. Editing and deleting each
     * trigger what they need themselves, so this is for places that
     * arrived some other way - an import, or a database that predates any
     * of this - and runs from the debug menu.
     */
    suspend fun enforceBoundaryForAll() {
        placeDao.getAllOnce()
            .filter { it.isNamed }
            .forEach { enforceBoundary(it.id) }
    }

    /**
     * Called after places are deleted, with the ids that were deleted.
     * Every ride and stop still pointing at one of them is re-resolved
     * from the point it actually recorded, so the label a deletion took
     * away comes straight back as a geohash placeholder rather than
     * leaving the row pointing at nothing.
     *
     * Deliberately targeted rather than a sweep for anything dangling:
     * imported rides can legitimately reference places that haven't been
     * imported yet (see RidesBackup), and re-resolving those would strand
     * them on placeholders that the real places, imported later, could no
     * longer reclaim. reattachOrphans() is the sweep, and it is manual.
     */
    suspend fun reattachDeleted(placeIds: Collection<String>) {
        if (placeIds.isEmpty()) return
        val gone = placeIds.toSet()
        reattach { it in gone }
    }

    /**
     * The unrestricted version: every ride and stop pointing at a place
     * that isn't in the database at all. This repairs rows orphaned
     * before deletion started re-resolving them, which is why it exists
     * only as a debug-menu action - see the caveat on reattachDeleted().
     */
    suspend fun reattachOrphans() {
        val known = placeDao.getAllOnce().mapTo(mutableSetOf()) { it.id }
        reattach { it !in known }
    }

    /** Step 1 - see enforceBoundary(). */
    private suspend fun pushOutPointsNowOutside(
        place: Place,
        touchedPlaceIds: MutableSet<String>,
    ) {
        for (ride in rideDao.getByPlaceId(place.id)) {
            var updated = ride
            var changed = false

            if (ride.startPlaceId == place.id) {
                val loc = ride.startLocation
                if (loc != null && !isWithin(loc.latitude, loc.longitude, place)) {
                    updated = updated.copy(
                        startPlaceId = resolve(loc, touchedPlaceIds)
                    )
                    changed = true
                }
            }

            if (ride.endPlaceId == place.id) {
                val loc = ride.endLocation
                if (loc != null && !isWithin(loc.latitude, loc.longitude, place)) {
                    updated = updated.copy(
                        endPlaceId = resolve(loc, touchedPlaceIds)
                    )
                    changed = true
                }
            }

            if (changed) {
                rideDao.update(updated)
            }
        }

        for (stop in stopDao.getByPlaceId(place.id)) {
            val loc = stop.location
            if (loc != null && !isWithin(loc.latitude, loc.longitude, place)) {
                stopDao.update(stop.copy(placeId = resolve(loc, touchedPlaceIds)))
            }
        }
    }

    /**
     * Step 2 - see enforceBoundary(). Reads every ride and stop rather
     * than the ones linked here, because the whole point is the ones that
     * are linked somewhere else.
     */
    private suspend fun claimPointsNowInside(
        place: Place,
        touchedPlaceIds: MutableSet<String>,
    ) {
        for (ride in rideDao.getAllOnce()) {
            var updated = ride
            var changed = false

            val start = ride.startLocation
            if (start != null && isWithin(start.latitude, start.longitude, place)) {
                val resolved = resolve(start, touchedPlaceIds)
                if (resolved != ride.startPlaceId) {
                    ride.startPlaceId?.let(touchedPlaceIds::add)
                    updated = updated.copy(startPlaceId = resolved)
                    changed = true
                }
            }

            val end = ride.endLocation
            if (end != null && isWithin(end.latitude, end.longitude, place)) {
                val resolved = resolve(end, touchedPlaceIds)
                if (resolved != ride.endPlaceId) {
                    ride.endPlaceId?.let(touchedPlaceIds::add)
                    updated = updated.copy(endPlaceId = resolved)
                    changed = true
                }
            }

            if (changed) {
                rideDao.update(updated)
            }
        }

        for (stop in stopDao.getAllOnce()) {
            val loc = stop.location ?: continue
            if (!isWithin(loc.latitude, loc.longitude, place)) continue

            val resolved = resolve(loc, touchedPlaceIds)
            if (resolved != stop.placeId) {
                stop.placeId?.let(touchedPlaceIds::add)
                stopDao.update(stop.copy(placeId = resolved))
            }
        }
    }

    /** Step 3 - see enforceBoundary(). */
    private suspend fun dropEmptiedPlaceholdersInside(place: Place) {
        val emptied = placeDao.getAllOnce()
            .filter { candidate ->
                candidate.id != place.id &&
                    !candidate.isNamed &&
                    isWithin(candidate.latitude, candidate.longitude, place)
            }
            .filter { candidate ->
                rideDao.getByPlaceId(candidate.id).isEmpty() &&
                    stopDao.getByPlaceId(candidate.id).isEmpty()
            }
            .map { it.id }

        if (emptied.isNotEmpty()) {
            placeDao.deleteByIds(emptied)
        }
    }

    /**
     * [isGone] answers "does this placeId need replacing?". A point that
     * was never recorded has nothing to resolve from, so its id is
     * cleared instead - that reads as `-` exactly as the dangling id did,
     * without leaving a reference to a place that isn't there.
     */
    private suspend fun reattach(isGone: (String) -> Boolean) {

        val touchedPlaceIds = mutableSetOf<String>()

        for (ride in rideDao.getAllOnce()) {
            var updated = ride
            var changed = false

            if (ride.startPlaceId?.let(isGone) == true) {
                updated = updated.copy(
                    startPlaceId = ride.startLocation?.let { resolve(it, touchedPlaceIds) }
                )
                changed = true
            }

            if (ride.endPlaceId?.let(isGone) == true) {
                updated = updated.copy(
                    endPlaceId = ride.endLocation?.let { resolve(it, touchedPlaceIds) }
                )
                changed = true
            }

            if (changed) {
                rideDao.update(updated)
            }
        }

        for (stop in stopDao.getAllOnce()) {
            if (stop.placeId?.let(isGone) == true) {
                stopDao.update(
                    stop.copy(placeId = stop.location?.let { resolve(it, touchedPlaceIds) })
                )
            }
        }

        touchedPlaceIds.forEach { placeLocationRecalculator.recompute(it) }
    }

    /**
     * One point through PlaceResolver, noting which place ends up holding
     * it so its average can be refreshed afterwards.
     */
    private suspend fun resolve(
        location: RideLocation,
        touchedPlaceIds: MutableSet<String>,
    ): String {
        val place = placeResolver.resolvePlace(location.latitude, location.longitude)
        touchedPlaceIds.add(place.id)
        return place.id
    }

    private fun isWithin(latitude: Double, longitude: Double, place: Place): Boolean =
        DistanceUtil.haversineMeters(
            latitude, longitude,
            place.latitude, place.longitude
        ) <= place.radiusMeters
}
