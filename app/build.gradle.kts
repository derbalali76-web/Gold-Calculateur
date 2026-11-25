plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Reads a value from gradle properties (-P / ~/.gradle/gradle.properties) or the environment. */
fun prop(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

// Google's public TEST ids. Debug builds ALWAYS use these. Never ship them in a release.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

android {
    namespace = "com.goldcalculateur.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goldcalculateur.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ---- Ad / billing policy knobs (change here, the calculation engine is untouched) ----
        buildConfigField("int", "INTERSTITIAL_EVERY_N_CALCS", "3")
        buildConfigField("long", "INTERSTITIAL_COOLDOWN_SECONDS", "180L")
        buildConfigField("String", "REMOVE_ADS_PRODUCT_ID", "\"remove_ads_lifetime\"")

    }

    val hasSigning = prop("RELEASE_STORE_FILE") != null
    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(prop("RELEASE_STORE_FILE")!!)
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            }
        }
    }

    fun String.q() = "\"$this\""

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", testBannerId.q())
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", testInterstitialId.q())
            buildConfigField("String", "PRIVACY_POLICY_URL", (prop("PRIVACY_POLICY_URL") ?: "").q())
            buildConfigField("String", "TERMS_URL", (prop("TERMS_URL") ?: "").q())
            buildConfigField("String", "CONTACT_EMAIL", (prop("CONTACT_EMAIL") ?: "").q())
            buildConfigField("boolean", "USING_TEST_ADS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID") ?: ""
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", (prop("ADMOB_BANNER_UNIT_ID") ?: "").q())
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", (prop("ADMOB_INTERSTITIAL_UNIT_ID") ?: "").q())
            buildConfigField("String", "PRIVACY_POLICY_URL", (prop("PRIVACY_POLICY_URL") ?: "").q())
            buildConfigField("String", "TERMS_URL", (prop("TERMS_URL") ?: "").q())
            buildConfigField("String", "CONTACT_EMAIL", (prop("CONTACT_EMAIL") ?: "").q())
            buildConfigField("boolean", "USING_TEST_ADS", "false")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { jvmToolchain(17) }

// ---------------------------------------------------------------------------------------
// Release guard: refuse to build a release with test ad ids, missing signing or placeholder
// legal URLs. (Evaluated at configuration time so it is configuration-cache friendly.)
// ---------------------------------------------------------------------------------------
val requiredForRelease = listOf(
    "ADMOB_APP_ID", "ADMOB_BANNER_UNIT_ID", "ADMOB_INTERSTITIAL_UNIT_ID",
    "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
)
val missingForRelease = requiredForRelease.filter { prop(it) == null }
val recommendedForRelease = listOf("PRIVACY_POLICY_URL", "TERMS_URL", "CONTACT_EMAIL")
val usesGoogleTestIds = listOf("ADMOB_APP_ID", "ADMOB_BANNER_UNIT_ID", "ADMOB_INTERSTITIAL_UNIT_ID")
    .any { prop(it)?.contains("3940256099942544") == true }
// Release ids must also look like real AdMob ids (publisher "ca-app-pub-<16 digits>").
val malformedAdIds = listOf("ADMOB_APP_ID", "ADMOB_BANNER_UNIT_ID", "ADMOB_INTERSTITIAL_UNIT_ID")
    .filter { prop(it)?.matches(Regex("""ca-app-pub-\d{16}[~/]\d+""")) == false }

val verifyReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails if release secrets/ids are missing or still Google's test ids."
    doLast {
        if (missingForRelease.isNotEmpty()) {
            throw GradleException("Release build blocked. Missing properties: $missingForRelease (see gradle.properties)")
        }
        val missingRecommended = recommendedForRelease.filter { prop(it) == null }
        if (missingRecommended.isNotEmpty()) {
            logger.warn("WARNING: not set (required before Play submission): $missingRecommended")
        }
        if (malformedAdIds.isNotEmpty()) {
            throw GradleException("Release build blocked: malformed AdMob ids: $malformedAdIds")
        }
        if (usesGoogleTestIds) {
            throw GradleException("Release build blocked: AdMob test ids must not be used in a release.")
        }
    }
}

tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    .configureEach { dependsOn(verifyReleaseConfig) }

dependencies {
    implementation(project(":engine"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.google.play.services.ads)
    implementation(libs.google.ump)
    implementation(libs.android.billing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
