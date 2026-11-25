package com.goldcalculateur.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goldcalculateur.app.core.CalculationQuota
import com.goldcalculateur.app.core.QuotaState
import com.goldcalculateur.engine.ScalePrecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gold_calculateur")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AdCounters(val completedSinceLastAd: Int, val lastInterstitialAtMillis: Long)

/** All local settings. Nothing here is ever sent to a server. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SCALE = intPreferencesKey("scale_decimals")
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val WORK_STATE = stringPreferencesKey("work_state")
        val COMPLETED_SINCE_AD = intPreferencesKey("completed_since_ad")
        val LAST_INTERSTITIAL = longPreferencesKey("last_interstitial_at")
        val LAST_COUNTED_SIGNATURE = stringPreferencesKey("last_counted_signature")
        val QUOTA_DAY = longPreferencesKey("quota_day")
        val QUOTA_USED = intPreferencesKey("quota_used")
        val QUOTA_BONUS = intPreferencesKey("quota_bonus")
    }

    private val safe: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e
    }

    val theme: Flow<ThemeMode> = safe.map { p ->
        ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: ThemeMode.SYSTEM
    }

    val scale: Flow<ScalePrecision> = safe.map { p ->
        ScalePrecision.fromDecimals(p[Keys.SCALE] ?: ScalePrecision.G_0_001.decimals)
    }

    /** Cached entitlement (lets the app start ad-free offline). Re-verified against Google Play. */
    val adsRemoved: Flow<Boolean> = safe.map { it[Keys.ADS_REMOVED] ?: false }

    val adCounters: Flow<AdCounters> = safe.map {
        AdCounters(it[Keys.COMPLETED_SINCE_AD] ?: 0, it[Keys.LAST_INTERSTITIAL] ?: 0L)
    }

    val quota: Flow<QuotaState> = safe.map {
        QuotaState(it[Keys.QUOTA_DAY] ?: 0L, it[Keys.QUOTA_USED] ?: 0, it[Keys.QUOTA_BONUS] ?: 0)
    }

    private suspend fun editQuota(f: (QuotaState) -> QuotaState) = store.edit {
        val n = f(QuotaState(it[Keys.QUOTA_DAY] ?: 0L, it[Keys.QUOTA_USED] ?: 0, it[Keys.QUOTA_BONUS] ?: 0))
        it[Keys.QUOTA_DAY] = n.day; it[Keys.QUOTA_USED] = n.used; it[Keys.QUOTA_BONUS] = n.bonus
    }

    suspend fun consumeQuota(rules: CalculationQuota, today: Long) = editQuota { rules.consume(it, today) }
    suspend fun grantQuotaBonus(rules: CalculationQuota, today: Long) = editQuota { rules.grant(it, today) }

    suspend fun workState(): String? = safe.first()[Keys.WORK_STATE]
    suspend fun lastCountedSignature(): String? = safe.first()[Keys.LAST_COUNTED_SIGNATURE]

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setScale(p: ScalePrecision) = store.edit { it[Keys.SCALE] = p.decimals }
    suspend fun setAdsRemoved(value: Boolean) = store.edit { it[Keys.ADS_REMOVED] = value }
    suspend fun saveWorkState(encoded: String) = store.edit { it[Keys.WORK_STATE] = encoded }

    /** A completed calculation (new inputs). Eligible for the interstitial counter. */
    suspend fun recordCompletedCalculation(signature: String) = store.edit {
        it[Keys.COMPLETED_SINCE_AD] = (it[Keys.COMPLETED_SINCE_AD] ?: 0) + 1
        it[Keys.LAST_COUNTED_SIGNATURE] = signature
    }

    suspend fun markInterstitialShown(nowMillis: Long) = store.edit {
        it[Keys.COMPLETED_SINCE_AD] = 0
        it[Keys.LAST_INTERSTITIAL] = nowMillis
    }
}
