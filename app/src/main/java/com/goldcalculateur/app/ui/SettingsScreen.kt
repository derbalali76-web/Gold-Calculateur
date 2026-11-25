package com.goldcalculateur.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldcalculateur.app.BuildConfig
import com.goldcalculateur.app.R
import com.goldcalculateur.app.billing.BillingStatus
import com.goldcalculateur.app.billing.BillingUiState
import com.goldcalculateur.app.core.AppLanguage
import com.goldcalculateur.app.data.ThemeMode
import com.goldcalculateur.app.ui.theme.extra
import com.goldcalculateur.engine.ScalePrecision

class SettingsActions(
    val onBack: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onScale: (ScalePrecision) -> Unit = {},
    val onBuy: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onPrivacyOptions: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onConsumeBillingStatus: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    language: AppLanguage,
    theme: ThemeMode,
    scale: ScalePrecision,
    adsRemoved: Boolean,
    billing: BillingUiState,
    privacyOptionsRequired: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler(onBack = actions.onBack)
    DisposableEffect(Unit) { onDispose { actions.onConsumeBillingStatus() } }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.testTag("settings_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.extra.goldText)
            }
            Text(
                stringResource(R.string.settings_title), color = MaterialTheme.extra.goldText,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- language
            Section(stringResource(R.string.language)) {
                Choice("ar", stringResource(R.string.lang_ar), language == AppLanguage.ARABIC) { actions.onLanguage(AppLanguage.ARABIC) }
                Choice("fr", stringResource(R.string.lang_fr), language == AppLanguage.FRENCH) { actions.onLanguage(AppLanguage.FRENCH) }
                Choice("en", stringResource(R.string.lang_en), language == AppLanguage.ENGLISH) { actions.onLanguage(AppLanguage.ENGLISH) }
            }
            // ---- appearance
            Section(stringResource(R.string.appearance)) {
                Choice("theme_dark", stringResource(R.string.theme_dark), theme == ThemeMode.DARK) { actions.onTheme(ThemeMode.DARK) }
                Choice("theme_light", stringResource(R.string.theme_light), theme == ThemeMode.LIGHT) { actions.onTheme(ThemeMode.LIGHT) }
                Choice("theme_system", stringResource(R.string.theme_system), theme == ThemeMode.SYSTEM) { actions.onTheme(ThemeMode.SYSTEM) }
            }
            // ---- remove ads
            Section(stringResource(R.string.remove_ads_title)) {
                Text(stringResource(R.string.remove_ads_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                if (adsRemoved) {
                    Text(
                        stringResource(R.string.remove_ads_done), color = MaterialTheme.extra.success,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("ads_removed_done"),
                    )
                } else {
                    val price = billing.priceText
                    Button(
                        onClick = actions.onBuy,
                        enabled = price != null && billing.status != BillingStatus.PURCHASING,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("buy_remove_ads"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.extra.gold, contentColor = MaterialTheme.extra.onGold,
                        ),
                    ) {
                        Text(
                            if (price != null) stringResource(R.string.remove_ads_button, price) else stringResource(R.string.remove_ads_title),
                            fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        )
                    }
                    if (price == null) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.remove_ads_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = actions.onRestore, modifier = Modifier.fillMaxWidth().testTag("restore_purchases")) {
                        Text(stringResource(R.string.restore_purchases))
                    }
                    billingMessage(billing.status)?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.testTag("billing_message"))
                    }
                }
                if (privacyOptionsRequired) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = actions.onPrivacyOptions, modifier = Modifier.fillMaxWidth().testTag("privacy_options")) {
                        Text(stringResource(R.string.privacy_options))
                    }
                }
            }
            // ---- history entry point (no third big button in the bottom bar)
            Section(stringResource(R.string.history_title)) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = actions.onOpenHistory).padding(vertical = 8.dp).testTag("open_history"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.extra.goldText)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.history_open), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            // ---- app info
            Section(stringResource(R.string.app_info)) {
                InfoRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                LinkRow(stringResource(R.string.privacy_policy), BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) { Links.openUrl(context, BuildConfig.PRIVACY_POLICY_URL) }
                LinkRow(stringResource(R.string.terms), BuildConfig.TERMS_URL.isNotBlank()) { Links.openUrl(context, BuildConfig.TERMS_URL) }
                LinkRow(stringResource(R.string.contact), BuildConfig.CONTACT_EMAIL.isNotBlank()) { Links.sendEmail(context, BuildConfig.CONTACT_EMAIL) }
                LinkRow(stringResource(R.string.rate_app), true) { Links.rateApp(context) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun billingMessage(status: BillingStatus): String? = when (status) {
    BillingStatus.PENDING -> stringResource(R.string.billing_pending)
    BillingStatus.CANCELLED -> stringResource(R.string.billing_cancelled)
    BillingStatus.NETWORK -> stringResource(R.string.billing_network)
    BillingStatus.UNAVAILABLE -> stringResource(R.string.billing_unavailable)
    BillingStatus.ERROR -> stringResource(R.string.billing_error)
    BillingStatus.RESTORED -> stringResource(R.string.billing_restored)
    BillingStatus.IDLE, BillingStatus.PURCHASING, BillingStatus.SUCCESS -> null
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SectionCard {
        Text(title, color = MaterialTheme.extra.goldText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Choice(tag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp).testTag("choice_$tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.extra.gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 10.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun LinkRow(label: String, configured: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = configured, role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (configured) MaterialTheme.extra.goldText else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        if (!configured) {
            Text(stringResource(R.string.not_configured), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}
