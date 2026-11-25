package com.goldcalculateur.app

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldcalculateur.app.core.CalcMode
import com.goldcalculateur.app.core.CalculationRunner
import com.goldcalculateur.app.core.IngotRow
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.core.WorkState
import com.goldcalculateur.app.ui.MainScreen
import com.goldcalculateur.app.ui.MainUiState
import com.goldcalculateur.app.ui.theme.GoldTheme
import com.goldcalculateur.engine.ScalePrecision
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders the real main screen (Arabic, RTL, dark) with the mockup's data and saves PNGs for review. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val rows = listOf(IngotRow(0, "100", "700"), IngotRow(1, "60", "720"), IngotRow(2, "40", "750"))

    private fun shot(name: String, mode: CalcMode) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale("ar")) }
        val arCtx = base.createConfigurationContext(cfg)
        val work = WorkState(rows, "748", mode, "1000")
        val scale = ScalePrecision.G_0_01
        val state = MainUiState(
            work = work, scale = scale, selectedCutIndex = null,
            liveSummary = CalculationRunner.liveSummary(work),
            result = CalculationRunner.run(work, scale, null), restored = true,
        )
        rule.setContent {
            CompositionLocalProvider(LocalContext provides arCtx, LocalLayoutDirection provides LayoutDirection.Rtl) {
                GoldTheme(dark = true) { MainScreen(state, NumberFormatter("ar"), com.goldcalculateur.app.ui.MainActions()) }
            }
        }
        rule.waitForIdle()
        val dir = base.getExternalFilesDir(null)!!
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun addMode() = shot("shot_add", CalcMode.ADD_INGOT)
    @Test fun cutMode() = shot("shot_cut", CalcMode.CUT_REFINE)
}
// screenshot trigger
// retry
// retry2
// art
// fix
// again
// larger UI
// r2
