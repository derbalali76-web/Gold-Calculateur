package com.goldcalculateur.app.core

/** Supported UI languages. */
enum class AppLanguage(val tag: String) {
    ARABIC("ar"), FRENCH("fr"), ENGLISH("en");

    val isRtl: Boolean get() = this == ARABIC

    companion object {
        /** Language to use for a system/app locale tag; unsupported languages fall back to English. */
        fun fromTag(tag: String?): AppLanguage {
            val lang = tag.orEmpty().lowercase().substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.tag == lang } ?: ENGLISH
        }
    }
}
