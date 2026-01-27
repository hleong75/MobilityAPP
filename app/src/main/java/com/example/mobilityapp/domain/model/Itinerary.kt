package com.example.mobilityapp.domain.model

import java.util.Date

data class Itinerary(
    val legs: List<Leg>,
    val startTime: Date?,
    val endTime: Date?,
    val distanceMeters: Double,
    val durationSeconds: Long
)
