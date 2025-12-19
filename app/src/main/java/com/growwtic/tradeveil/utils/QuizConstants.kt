package com.growwtic.tradeveil.services.com.example.tradeveil.utils

object QuizConstants {
    const val TOTAL_QUIZZES = 50
    const val TIME_PER_QUESTION = 30000L  // 30 seconds
    const val MAX_ATTEMPTS = 4
    val ATTEMPT_POINTS = listOf(100, 50, 25, 0)  // Points per attempt
    const val COMPLETION_BONUS = 500
}