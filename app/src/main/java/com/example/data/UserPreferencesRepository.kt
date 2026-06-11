package com.example.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun getMilesPerWeek(): Int {
        return prefs.getInt("miles_per_week", 250)
    }

    fun setMilesPerWeek(miles: Int) {
        prefs.edit().putInt("miles_per_week", miles).apply()
    }

    fun getNotificationTime(): Pair<Int, Int> { // Returns hour and minute
        val timeStr = prefs.getString("notification_time", "08:00") ?: "08:00"
        val parts = timeStr.split(":")
        return Pair(parts[0].toIntOrNull() ?: 8, parts[1].toIntOrNull() ?: 0)
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        val timeStr = String.format("%02d:%02d", hour, minute)
        prefs.edit().putString("notification_time", timeStr).apply()
    }
}
