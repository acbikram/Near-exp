package com.nearexpiry.manager.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
    fun getCurrentLanguage(): AppLanguage = runCatching {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        when {
            tags.isEmpty() -> AppLanguage.SYSTEM_DEFAULT
            tags.startsWith("ar") -> AppLanguage.ARABIC
            tags.startsWith("en") -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM_DEFAULT
        }
    }.getOrDefault(AppLanguage.SYSTEM_DEFAULT)

    /**
     * True if the app is currently displaying Arabic — either because the
     * user explicitly chose Arabic, or because "System Default" resolves to
     * an Arabic device locale.
     */
    fun isArabic(): Boolean = when (getCurrentLanguage()) {
        AppLanguage.ARABIC -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM_DEFAULT -> Locale.getDefault().language == "ar"
    }

    /** Applies the chosen language app-wide (recreates activities to apply it). */
    fun setLanguage(language: AppLanguage) {
        // Per-app locale support is optional on older OEM Android builds. A
        // failed locale hand-off must leave the app usable in its current
        // system language instead of terminating the activity.
        runCatching {
            val locales = if (language.tag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
