package com.goldcalculateur.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goldcalculateur.app.AppContainer
import com.goldcalculateur.app.BuildConfig
import com.goldcalculateur.app.MainActivity
import com.goldcalculateur.app.R
import com.goldcalculateur.app.ads.AdaptiveBanner
import com.goldcalculateur.app.core.AppLanguage
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.data.ThemeMode
import kotlinx.coroutines.launch

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"

/**
 * Navigation graph: Main (single complete screen) / Settings / History.
 *
 * The interstitial hook is [AdsManager.onNaturalTransition], invoked ONLY when the user navigates
 * back to the calculator from Settings or History (a real, user-initiated screen change).
 */
@Composable
fun AppRoot(activity: MainActivity, container: AppContainer, vm: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by vm.state.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(state)
    val history by vm.history.collectAsStateWithLifecycle()
    val theme by container.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val adsRemoved by container.settings.adsRemoved.collectAsStateWithLifecycle(initialValue = false)
    val billing by container.billing.state.collectAsStateWithLifecycle()
    val canRequestAds by container.ads.canRequestAds.collectAsStateWithLifecycle()
    val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0].toLanguageTag())
    val nf = remember(language) { NumberFormatter(language.tag) }

    fun goBack() {
        val returningTo = navController.previousBackStackEntry?.destination?.route
        navController.popBackStack()
        if (returningTo == ROUTE_MAIN) container.ads.onNaturalTransition(activity)
    }

    val mainActions = remember(vm, nf) {
        MainActions(
            onWeightChange = vm::onWeightChange,
            onKaratChange = vm::onKaratChange,
            onAddRow = vm::addRow,
            onRemoveRow = vm::removeRow,
            onTargetChange = vm::onTargetChange,
            onModeChange = vm::setMode,
            onAddedKaratChange = vm::onAddedKaratChange,
            onSelectCutIngot = vm::selectCutIngot,
            onCalculate = vm::calculate,
            onClearAll = vm::clearAll,
            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
            onCopy = {
                buildResultText(context, nf, latest)?.let { text ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                buildResultText(context, nf, latest)?.let { text ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            },
        )
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIN,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(ROUTE_MAIN) {
            MainScreen(
                state = state, nf = nf, actions = mainActions,
                bannerSlot = {
                    // The real AdMob adaptive banner. Hidden for buyers and while consent/SDK is not ready.
                    if (canRequestAds && !adsRemoved) {
                        AdaptiveBanner(unitId = BuildConfig.ADMOB_BANNER_UNIT_ID)
                    }
                },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                language = language, theme = theme, scale = state.scale,
                adsRemoved = adsRemoved, billing = billing, privacyOptionsRequired = privacyOptionsRequired,
                actions = SettingsActions(
                    onBack = ::goBack,
                    onLanguage = { lang ->
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                    },
                    onTheme = { mode -> scope.launch { container.settings.setTheme(mode) } },
                    onScale = { p -> scope.launch { container.settings.setScale(p) } },
                    onBuy = { container.billing.launchPurchase(activity) },
                    onRestore = { container.billing.refreshPurchases(userInitiated = true) },
                    onPrivacyOptions = { container.ads.showPrivacyOptions(activity) },
                    onOpenHistory = { navController.navigate(ROUTE_HISTORY) { launchSingleTop = true } },
                    onConsumeBillingStatus = container.billing::consumeStatus,
                ),
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                entries = history, nf = nf,
                onBack = ::goBack,
                onOpen = { id ->
                    vm.openHistoryEntry(id) {
                        navController.popBackStack(ROUTE_MAIN, inclusive = false)
                        container.ads.onNaturalTransition(activity)
                    }
                },
                onDelete = vm::deleteHistoryEntry,
                onClear = vm::clearHistory,
            )
        }
    }
}
