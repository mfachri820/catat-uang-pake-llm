package com.catatuang.app.data

import android.content.Context

/** Small key-value settings. The API key stays on the device in app-private storage. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var openRouterKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var jevModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** Below this confidence the review screen flags the category as uncertain. */
    var confidenceThreshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_THRESHOLD, value).apply()

    /** Monthly budget in rupiah; 0 means no budget. Shown on the widget. */
    var monthlyBudget: Long
        get() = prefs.getLong(KEY_BUDGET, 0L)
        set(value) = prefs.edit().putLong(KEY_BUDGET, value).apply()

    companion object {
        const val DEFAULT_MODEL = "~typesafe/jev-latest"
        private const val KEY_API = "openrouter_key"
        private const val KEY_MODEL = "jev_model"
        private const val KEY_THRESHOLD = "confidence_threshold"
        private const val KEY_BUDGET = "monthly_budget"
    }
}
