package com.critterfarm.data

import android.content.Context

/** Whether the user has been through onboarding at least once, so it isn't shown every launch. */
class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED, value).apply()

    private companion object {
        const val PREFS_NAME = "critterfarm_prefs"
        const val KEY_COMPLETED = "onboarding_completed"
    }
}
