package com.example.mobilityapp.domain.model

data class Instruction(
    val text: String,
    val distanceMeters: Double,
    val durationSeconds: Long
)
