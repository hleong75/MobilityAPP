package com.example.mobilityapp.domain.model

data class Leg(
    val mode: TravelMode,
    val instructions: List<Instruction>,
    val distanceMeters: Double,
    val durationSeconds: Long
)
