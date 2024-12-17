package org.lyaaz.fuckclip

import android.content.SharedPreferences

class Settings private constructor(private val prefs: SharedPreferences) {

    fun isEnabled(packageName: String): Boolean {
        return prefs.getBoolean(packageName, false)
    }

    companion object {
        @Volatile
        private var INSTANCE: Settings? = null
        fun getInstance(prefs: SharedPreferences): Settings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Settings(prefs).also { INSTANCE = it }
            }
        }
    }
}