package com.goldcalculateur.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.goldcalculateur.app.data.ThemeMode
import com.goldcalculateur.app.ui.AppRoot
import com.goldcalculateur.app.ui.MainViewModel
import com.goldcalculateur.app.ui.theme.GoldTheme
import kotlinx.coroutines.launch

/** AppCompatActivity (a ComponentActivity) is required for in-app language switching. */
class MainActivity : AppCompatActivity() {

    private val container: AppContainer get() = (application as GoldApplication).container

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container.settings, container.database, container.quota) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Consent + ads initialisation are skipped entirely for buyers (entitlement is cached locally).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.adsRemoved.collect { removed ->
                    if (!removed) container.ads.gatherConsent(this@MainActivity)
                }
            }
        }

        setContent {
            val theme: ThemeMode? by container.settings.theme.collectAsStateWithLifecycle(initialValue = null)
            val mode = theme
            if (mode != null) {
                val dark = when (mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                GoldTheme(dark = dark) {
                    AppRoot(activity = this@MainActivity, container = container, vm = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // App Open ad: only when returning from the background after a long idle, never mid-operation.
        container.ads.onAppForegrounded(this, userMidOperation = viewModel.hasInputs())
    }

    override fun onStop() {
        super.onStop()
        container.ads.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        container.billing.refreshPurchases() // restores/refreshes the entitlement (re-installs, refunds)
    }
}
