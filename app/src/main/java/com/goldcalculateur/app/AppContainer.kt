package com.goldcalculateur.app

import android.app.Application
import androidx.room.Room
import com.goldcalculateur.app.ads.AdsManager
import com.goldcalculateur.app.billing.BillingManager
import com.goldcalculateur.app.data.AppDatabase
import com.goldcalculateur.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container (kept deliberately simple: no DI framework needed). */
class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(app)
    val database: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "gold_calculateur.db").build()
    val ads = AdsManager(app, settings, scope)
    val billing = BillingManager(app, scope, settings)

    init {
        // Single source of truth for "ads removed": the cached/verified entitlement.
        scope.launch { settings.adsRemoved.collect { ads.setAdsRemoved(it) } }
        billing.start()
    }
}
