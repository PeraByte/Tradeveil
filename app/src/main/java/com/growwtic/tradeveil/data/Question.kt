package com.growwtic.tradeveil.services.com.example.tradeveil.data

data class Question(
    val question: String,
    val A: String,
    val B: String,
    val C: String,
    val D: String,
    val answer: String  // Will be "A", "B", "C", or "D"
)