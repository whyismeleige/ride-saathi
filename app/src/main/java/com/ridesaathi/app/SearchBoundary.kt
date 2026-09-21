package com.ridesaathi.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A straight-line radius around a recent device position, not an administrative city border. */
object SearchBoundary {
    const val RADIUS_METERS = 50_000

    fun valid(point: PlaceCandidate): Boolean = point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    fun contains(center: PlaceCandidate, point: PlaceCandidate): Boolean {
        if (!valid(center) || !valid(point)) return false
        val latitudeDelta = Math.toRadians(point.latitude - center.latitude)
        val longitudeDelta = Math.toRadians(point.longitude - center.longitude)
        val a = (sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(Math.toRadians(center.latitude)) * cos(Math.toRadians(point.latitude)) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)).coerceIn(0.0, 1.0)
        val distance = 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
        return distance <= RADIUS_METERS
    }

    /** Preserve relevance order while rejecting distant or invalid coordinates. */
    fun filter(center: PlaceCandidate, candidates: List<PlaceCandidate>): List<PlaceCandidate> =
        candidates.filter { contains(center, it) }
}
