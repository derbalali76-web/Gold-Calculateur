package com.goldcalculateur.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldcalculateur.app.billing.BillingUiState
import com.goldcalculateur.app.core.AppLanguage
import com.goldcalculateur.app.core.CalcMode
import com.goldcalculateur.app.core.CalculationRunner
import com.goldcalculateur.app.core.IngotRow
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.core.WorkState
import com.goldcalculateur.app.data.ThemeMode
import com.goldcalculateur.app.ui.MainActions
import com.goldcalculateur.app.ui.MainScreen
import com.goldcalculateur.app.ui.MainUiState
import com.goldcalculateur.app.ui.SettingsActions
import com.goldcalculateur.app.ui.SettingsScreen
import com.goldcalculateur.app.ui.theme.GoldTheme
import com.goldcalculateur.engine.ScalePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the main parts. They need an emulator/device (`./gradlew connectedDebugAndroidTest`,
 * run by the manual "Instrumented tests" GitHub workflow).
 * Dataset: the printed figures of the brief correspond to 40 g @ 742 (see engine tests).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule val rule = createComposeRule()

    private val rows = listOf(IngotRow(0, "100", "700"), IngotRow(1, "60", "720"), IngotRow(2, "40", "742"))

    private fun work(mode: CalcMode, target: String = "748", ka: String = "1000") =
        WorkState(rows, target, mode, ka)

    private fun stateFor(work: WorkState, calculated: Boolean, selectedCut: Int? = null): MainUiState {
        val scale = ScalePrecision.G_0_001
        return MainUiState(
            work = work, scale = scale, selectedCutIndex = selectedCut,
            liveSummary = CalculationRunner.liveSummary(work),
            result = if (calculated) CalculationRunner.run(work, scale, selectedCut) else null,
            restored = true,
        )
    }

    @Composable
    private fun Screen(
        state: MainUiState, actions: MainActions = MainActions(),
        rtl: Boolean = false, lang: String = "en", modifier: Modifier = Modifier,
        banner: @Composable () -> Unit = {},
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            GoldTheme(dark = true) {
                MainScreen(state, NumberFormatter(lang), actions, modifier, bannerSlot = banner)
            }
        }
    }

    @Test fun weightedAverageAndTotalAreShownLive() {
        rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false)) }
        rule.onNodeWithTag("summary_average").assertTextContains("714.4", substring = true)
        rule.onNodeWithTag("summary_total").assertTextContains("200", substring = true)
    }

    @Test fun averageHasNoSlash1000() {
        rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false)) }
        val texts = rule.onNodeWithTag("summary_average").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
        assertTrue(!texts.contains("/1000"))
    }

    @Test fun addModeResultMatchesTheEngine() {
        rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = true)) }
        rule.onNodeWithTag("result_card").assertIsDisplayed()
        rule.onNodeWithTag("add_weight").assertTextContains("26.67", substring = true)
        rule.onNodeWithTag("final_karat").assertTextContains("748.00", substring = true)
    }

    @Test fun cutModeResultAndDropdown() {
        var selected = -1
        rule.setContent {
            Screen(
                stateFor(work(CalcMode.CUT_REFINE), calculated = true),
                MainActions(onSelectCutIngot = { selected = it }),
            )
        }
        rule.onNodeWithTag("cut_weight").assertTextContains("29.95", substring = true)
        rule.onNodeWithTag("final_karat").assertTextContains("748.00", substring = true)
        rule.onNodeWithTag("cut_ingot_dropdown").performClick()
        rule.onNodeWithTag("cut_option_2").performClick()
        assertEquals(2, selected)
    }

    @Test fun insufficientIngotShowsAClearWarningAndKeepsTheDropdown() {
        val w = WorkState(listOf(IngotRow(0, "5", "800"), IngotRow(1, "95", "500")), "900", CalcMode.CUT_REFINE, "1000")
        rule.setContent { Screen(stateFor(w, calculated = true, selectedCut = 0)) }
        rule.onNodeWithTag("cut_unusable").assertIsDisplayed()
        rule.onNodeWithTag("cut_ingot_dropdown").assertIsDisplayed()
    }

    @Test fun impossibleCalculationShowsAnErrorNotAResult() {
        val w = work(CalcMode.ADD_INGOT, target = "748", ka = "748")
        rule.setContent { Screen(stateFor(w, calculated = true)) }
        rule.onNodeWithTag("result_error").assertIsDisplayed()
    }

    @Test fun modeSwitchAndCalculateInvokeCallbacks() {
        var mode: CalcMode? = null
        var calculated = 0
        rule.setContent {
            Screen(
                stateFor(work(CalcMode.ADD_INGOT), calculated = false),
                MainActions(onModeChange = { mode = it }, onCalculate = { calculated++ }),
            )
        }
        rule.onNodeWithTag("mode_cut").performClick()
        assertEquals(CalcMode.CUT_REFINE, mode)
        rule.onNodeWithTag("calculate").performClick()
        assertEquals(1, calculated)
    }

    @Test fun addedKaratChipsAreOnlyInAddMode() {
        rule.setContent { Screen(stateFor(work(CalcMode.CUT_REFINE), calculated = false)) }
        rule.onNodeWithTag("chip_875").assertDoesNotExist()
    }

    @Test fun quickKaratChipChangesTheFieldInPlace() {
        var karat: String? = null
        rule.setContent {
            Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false), MainActions(onAddedKaratChange = { karat = it }))
        }
        rule.onNodeWithTag("chip_875").performClick()
        assertEquals("875", karat)
    }

    @Test fun typingInACellReportsTheChange() {
        var typed: String? = null
        rule.setContent {
            Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false), MainActions(onWeightChange = { _, t -> typed = t }))
        }
        rule.onNodeWithTag("weight_1").performTextInput("5")
        assertTrue(typed != null)
    }

    @Test fun addAndDeleteRowButtons() {
        var added = 0
        var removed: Long? = null
        rule.setContent {
            Screen(
                stateFor(work(CalcMode.ADD_INGOT), calculated = false),
                MainActions(onAddRow = { added++ }, onRemoveRow = { removed = it }),
            )
        }
        rule.onNodeWithTag("add_row").performClick()
        rule.onNodeWithTag("delete_2").performClick()
        assertEquals(1, added)
        assertEquals(1L, removed)
    }

    @Test fun clearAllAsksForConfirmation() {
        var cleared = 0
        rule.setContent {
            Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false), MainActions(onClearAll = { cleared++ }))
        }
        rule.onNodeWithTag("clear_all").performClick()
        assertEquals(0, cleared) // not yet: needs confirmation
        rule.onNodeWithTag("confirm_clear").performClick()
        assertEquals(1, cleared)
    }

    @Test fun adSlotSitsBelowTheControlsAndNeverOverlapsThem() {
        rule.setContent {
            Screen(
                stateFor(work(CalcMode.ADD_INGOT), calculated = false),
                banner = { Box(Modifier.fillMaxWidth().height(50.dp).testTag("ad_slot")) },
            )
        }
        val calc = rule.onNodeWithTag("calculate").getUnclippedBoundsInRoot()
        val ad = rule.onNodeWithTag("ad_slot").getUnclippedBoundsInRoot()
        assertTrue("banner must be below the calculate button", ad.top >= calc.bottom)
        val settings = rule.onNodeWithTag("open_settings").getUnclippedBoundsInRoot()
        assertTrue(ad.top >= settings.bottom)
    }

    @Test fun noAdSlotMeansNoReservedSpaceOrCrash() {
        rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false)) }
        rule.onNodeWithTag("calculate").assertIsDisplayed()
    }

    @Test fun settingsStaysAtThePhysicalLeftInRtlAndLtr() {
        for (rtl in listOf(true, false)) {
            rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = false), rtl = rtl, lang = if (rtl) "ar" else "fr") }
            val s = rule.onNodeWithTag("open_settings").getUnclippedBoundsInRoot()
            val c = rule.onNodeWithTag("calculate").getUnclippedBoundsInRoot()
            assertTrue(s.left < c.left)
        }
    }

    @Test fun smallPhone() {
        rule.setContent { Screen(stateFor(work(CalcMode.ADD_INGOT), calculated = true), modifier = Modifier.size(320.dp, 568.dp)) }
        rule.onNodeWithTag("calculate").assertIsDisplayed()
        rule.onNodeWithTag("target").assertIsDisplayed()
    }

    @Test fun largePhone() {
        rule.setContent { Screen(stateFor(work(CalcMode.CUT_REFINE), calculated = true), modifier = Modifier.size(412.dp, 915.dp)) }
        rule.onNodeWithTag("calculate").assertIsDisplayed()
        rule.onNodeWithTag("result_card").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ settings screen

    @Test fun settingsLanguageThemeAndScaleCallbacks() {
        var lang: AppLanguage? = null
        var theme: ThemeMode? = null
        var scale: ScalePrecision? = null
        rule.setContent {
            GoldTheme(dark = true) {
                SettingsScreen(
                    language = AppLanguage.ENGLISH, theme = ThemeMode.SYSTEM, scale = ScalePrecision.G_0_001,
                    adsRemoved = false, billing = BillingUiState(priceText = "$1.99"), privacyOptionsRequired = false,
                    actions = SettingsActions(onLanguage = { lang = it }, onTheme = { theme = it }, onScale = { scale = it }),
                )
            }
        }
        rule.onNodeWithTag("choice_ar").performClick()
        rule.onNodeWithTag("choice_theme_light").performClick()
        assertEquals(AppLanguage.ARABIC, lang)
        assertEquals(ThemeMode.LIGHT, theme)
    }

    @Test fun removeAdsButtonNeedsARealPlayPrice() {
        rule.setContent {
            GoldTheme(dark = true) {
                SettingsScreen(
                    AppLanguage.ENGLISH, ThemeMode.SYSTEM, ScalePrecision.G_0_001, adsRemoved = false,
                    billing = BillingUiState(priceText = null), privacyOptionsRequired = false, actions = SettingsActions(),
                )
            }
        }
        rule.onNodeWithTag("buy_remove_ads").assertIsNotEnabled()
    }

    @Test fun removeAdsButtonShowsTheLocalPriceAndBuys() {
        var bought = 0
        rule.setContent {
            GoldTheme(dark = true) {
                SettingsScreen(
                    AppLanguage.FRENCH, ThemeMode.DARK, ScalePrecision.G_0_001, adsRemoved = false,
                    billing = BillingUiState(priceText = "1,99 US$"), privacyOptionsRequired = false,
                    actions = SettingsActions(onBuy = { bought++ }),
                )
            }
        }
        rule.onNodeWithTag("buy_remove_ads").assertIsEnabled().assertTextContains("1,99 US$", substring = true)
        rule.onNodeWithTag("buy_remove_ads").performClick()
        assertEquals(1, bought)
    }

    @Test fun afterPurchaseTheBuyButtonIsReplacedByAThankYou() {
        rule.setContent {
            GoldTheme(dark = true) {
                SettingsScreen(
                    AppLanguage.ENGLISH, ThemeMode.DARK, ScalePrecision.G_0_001, adsRemoved = true,
                    billing = BillingUiState(), privacyOptionsRequired = false, actions = SettingsActions(),
                )
            }
        }
        rule.onNodeWithTag("ads_removed_done").assertIsDisplayed()
        rule.onNodeWithTag("buy_remove_ads").assertDoesNotExist()
    }

    @Test fun settingsHasNoDeveloperSupportSection() {
        rule.setContent {
            GoldTheme(dark = true) {
                SettingsScreen(
                    AppLanguage.ENGLISH, ThemeMode.DARK, ScalePrecision.G_0_001, adsRemoved = false,
                    billing = BillingUiState(), privacyOptionsRequired = false, actions = SettingsActions(),
                )
            }
        }
        rule.onNodeWithTag("developer_support").assertDoesNotExist()
    }
}
