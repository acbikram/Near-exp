package com.nearexpiry.manager.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Wraps [AppCompatDelegate]'s per-app language API.
 *
 * Setting a locale here persists automatically (via
 * AppLocalesMetadataHolderService, registered in the manifest) and
 * recreates all activities to apply the new locale immediately —
 * no extra storage or restart logic needed.
 */
object LanguageManager {

    enum class AppLanguage(val tag: String?) {
        SYSTEM_DEFAULT(null),
        ENGLISH("en"),
        ARABIC("ar")
    }

    /** Currently selected app language (defaults to SYSTEM_DEFAULT if none set). */
    fun getCurrentLanguage(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tags.isEmpty() -> AppLanguage.SYSTEM_DEFAULT
            tags.startsWith("ar") -> AppLanguage.ARABIC
            tags.startsWith("en") -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM_DEFAULT
        }
    }

    /** Applies the chosen language app-wide (recreates activities to apply it). */
    fun setLanguage(language: AppLanguage) {
        val locales = if (language.tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
