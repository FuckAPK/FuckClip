package org.lyaaz.fuckclip

import android.content.SharedPreferences

class Settings private constructor(private val prefs: SharedPreferences) {

    fun isEnabled(packageName: String): Boolean {
        return prefs.getBoolean(packageName, false)
    }

    companion object {
        fun getInstance(prefs: SharedPreferences): Settings = Settings(prefs)
    }
}
