package com.pulseloop.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Ported from OpenAIKeychainStore in the iOS app.
 * Stores the OpenAI API key securely using EncryptedSharedPreferences
 * (Android equivalent of iOS Keychain).
 */
class ApiKeyStore(context: Context) {
    private val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "pulseloop_secure",
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API_KEY, value).apply() }

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    var model: String
        get() = prefs.getString(KEY_MODEL, "gpt-5.4") ?: "gpt-5.4"
        set(value) { prefs.edit().putString(KEY_MODEL, value).apply() }

    var coachEnabled: Boolean
        get() = prefs.getBoolean(KEY_COACH_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_COACH_ENABLED, value).apply() }

    var webSearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_SEARCH, false)
        set(value) { prefs.edit().putBoolean(KEY_WEB_SEARCH, value).apply() }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING, value).apply() }

    var demoDataSeeded: Boolean
        get() = prefs.getBoolean(KEY_DEMO_SEEDED, false)
        set(value) { prefs.edit().putBoolean(KEY_DEMO_SEEDED, value).apply() }

    companion object {
        private const val KEY_API_KEY = "openai_api_key"
        private const val KEY_MODEL = "coach_model"
        private const val KEY_COACH_ENABLED = "coach_enabled"
        private const val KEY_WEB_SEARCH = "web_search_enabled"
        private const val KEY_ONBOARDING = "onboarding_completed"
        private const val KEY_DEMO_SEEDED = "demo_data_seeded"
    }
}
