package com.koasac.tradeveil.services.com.example.tradeveil.utils

// JsonParser.kt
import android.content.Context
import com.koasac.tradeveil.R
import com.koasac.tradeveil.services.com.example.tradeveil.data.Question
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.InputStreamReader

object JsonParser {
    fun parseQuestions(context: Context): List<Question> {
        val inputStream: InputStream = context.resources.openRawResource(R.raw.questions)
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<Question>>() {}.type
        return Gson().fromJson(reader, type)
    }
}