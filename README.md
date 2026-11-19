# Gold Calculateur

حاسبة تعديل عيار الذهب للصاغة — تطبيق Android أصلي (Kotlin + Jetpack Compose + Material 3).
*Gold karat adjustment calculator for jewellers — native Android app.*

- اللغات: العربية (RTL) · Français · English
- يعمل دون إنترنت؛ لا Firebase ولا تسجيل دخول ولا اشتراك.
- طريقتان: **إضافة سبيكة** و**قص وتنقية** — بدقة `BigDecimal` ودقة ميزان قابلة للضبط.
- الإعلانات: **بانر AdMob متكيف** أسفل الشاشة (هو نفسه مساحة الإعلان؛ لا توجد مساحة إعلانية مرسومة) + إعلان بيني واحد بعد كل 3 عمليات حساب مكتملة، يُعرض فقط عند انتقال طبيعي (الرجوع من الإعدادات أو السجل إلى الشاشة الرئيسية) مع فترة تهدئة 180 ثانية.
- لا Rewarded ولا App Open ولا Native.
- شراء لمرة واحدة `remove_ads_lifetime` (1.99 USD، يُعرض السعر المحلي من Google Play) لإزالة كل الإعلانات.

## بنية المشروع

```
engine/      وحدة Kotlin/JVM نقية: محرك الحساب (BigDecimal) + اختباراته. لا تعرف شيئاً عن الواجهة أو الإعلانات أو الشراء.
app/         تطبيق Android
  core/        منطق نقي قابل للاختبار: المحلل، التنسيق، ترميز الحالة، سياسة الإعلان البيني، منطق الاستحقاق
  data/        DataStore (الإعدادات) + Room (آخر 50 عملية)
  ads/         AdsManager (UMP + AdMob) و AdaptiveBanner
  billing/     BillingManager (Google Play Billing)
  ui/          Compose: الشاشة الرئيسية، الإعدادات، السجل، التنقل
docs/        سياسة الخصوصية (HTML ثلاثي اللغات) ودليل النشر
store/       نصوص المتجر (ar/fr/en)، نموذج Data Safety، الأيقونة 512 ورسم الميزة
tools/       مولّد الأيقونات
```

## البناء والاختبار

يتطلب JDK 17 وAndroid SDK (`ANDROID_HOME`).

```bash
./gradlew :engine:test :app:testDebugUnitTest   # اختبارات الوحدة
./gradlew :app:assembleDebug                    # APK تجريبي (إعلانات اختبار Google فقط)
./gradlew :app:connectedDebugAndroidTest        # اختبارات Compose UI (تحتاج محاكياً/جهازاً)
```

GitHub Actions: `CI` (اختبارات + Debug APK عند كل push) — `Instrumented UI tests` (يدوي، محاكي) — `Release bundle` (يدوي، AAB موقّع من أسرار GitHub).

## الإعدادات السرّية (لا تُرفع إلى git)

نسخة **Debug** تستخدم دائماً معرّفات الاختبار العامة من Google. نسخة **Release** ترفض البناء حتى تُضبط القيم التالية في `~/.gradle/gradle.properties` أو متغيرات البيئة (أو أسرار GitHub Actions):

`ADMOB_APP_ID` · `ADMOB_BANNER_UNIT_ID` · `ADMOB_INTERSTITIAL_UNIT_ID` · `PRIVACY_POLICY_URL` · `TERMS_URL` · `CONTACT_EMAIL` · `RELEASE_STORE_FILE` · `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` · `RELEASE_KEY_PASSWORD`

تكرار الإعلان البيني وفترة التهدئة يُعدّلان من `defaultConfig` في `app/build.gradle.kts`
(`INTERSTITIAL_EVERY_N_CALCS`, `INTERSTITIAL_COOLDOWN_SECONDS`) دون لمس محرك الحساب.

## معادلات الحساب

`W` الوزن الكلي، `P = Σ(وزن × عيار)`، `Kc = P / W`، `Kt` الهدف.

- إضافة سبيكة عيارها `Ka`: `Wa = W·(Kt − Kc) / (Ka − Kt)`
- قص وتنقية من سبيكة عيارها `Ki`: `x = W·(Kt − Kc) / [Kt·(1 − Ki/1000)]`
  — الذهب المسترجع `x·Ki/1000` — الوزن النهائي `W − x + x·Ki/1000` — العيار النهائي `P / الوزن النهائي = Kt`

> **ملاحظة عن مثال المواصفات:** السبائك المكتوبة (100@700 + 60@720 + 40@750) تعطي عياراً متوسطاً **716** وليس 714.4.
> الأرقام المطبوعة (714.4، 142.88، 26.6667، 44.2105، 29.9465) تطابق السبيكة الثالثة بعيار **742**. الاختباران
> مُثبتان في `engine/src/test/.../GoldEngineTest.kt` (`spec` = 742 و `literal` = 750).

انظر `docs/PUBLISHING.md` لخطوات النشر على Google Play.
