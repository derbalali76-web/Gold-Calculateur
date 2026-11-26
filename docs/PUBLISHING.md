# دليل النشر على Google Play

> **تنبيه:** هذه الخطوات مبنية على معرفتي بمتطلبات Google Play وقت الكتابة، ولم أستطع من بيئة العمل فتح Play Console أو صفحات Google للتحقق منها.
> راجع قبل الإرسال: مستوى API المستهدف المطلوب، إصدار Play Billing Library المطلوب، متطلبات الاختبار المغلق للحسابات الجديدة، ونموذج Data Safety الحالي.
> إن رفض Gradle إصداراً في `gradle/libs.versions.toml` فحدّثه إلى أحدث إصدار مستقر.

## 1) حساب AdMob
1. أنشئ تطبيق Android في AdMob (يمكن إنشاؤه قبل النشر ثم ربطه بصفحة المتجر لاحقاً).
2. أنشئ وحدتين: **Banner** و **Interstitial**. لا تنشئ Rewarded/App Open/Native.
3. ضع المعرّفات في `~/.gradle/gradle.properties` أو أسرار GitHub (انظر README). **لا تستخدمها قبل اختبار التطبيق بإعلانات الاختبار.**
4. عند الاختبار على جهازك بمعرّفات حقيقية أضفه كجهاز اختبار في AdMob لتفادي النقرات الذاتية (مخالفة سياسة).
5. فعّل رسائل الموافقة (GDPR / US states) من AdMob → Privacy & messaging، فالتطبيق يستخدم UMP.

## 2) إنشاء التطبيق في Play Console
- معرّف الحزمة: `com.goldcalculateur.app` — **لا يمكن تغييره بعد أول رفع**؛ غيّره في `app/build.gradle.kts` (`applicationId` و`namespace`) إن أردت معرّفاً يخصك قبل الرفع الأول.
- الوصف والنصوص: `store/listing_ar.md` / `listing_fr.md` / `listing_en.md`.
- الأيقونة: `store/play_store_icon_512.png` — رسم الميزة: `store/feature_graphic_1024x500.png`.
- **لقطات الشاشة:** يجب التقاطها من التطبيق الحقيقي (لم أستطع تشغيله هنا). شغّل `tools/capture_screenshots.sh` مع محاكي/هاتف (عربي وفرنسي وإنجليزي، 2–8 لقطات للهاتف).
- سياسة الخصوصية: استضف `docs/privacy/index.html` (مثلاً GitHub Pages) وضع الرابط في Play Console وفي `PRIVACY_POLICY_URL`، واستبدل `derbalali.bjtr@gmail.com` ببريدك.
- نموذج Data Safety: `store/data_safety.md`.
- Ads: «التطبيق يحتوي على إعلانات = نعم». الفئة العمرية: بالغون (لا تستهدف الأطفال). تصنيف المحتوى: أجب عن الاستبيان (لا محتوى حساس).

## 3) المنتج داخل التطبيق
Play Console → Monetize → In-app products → **Create product**
- Product ID: `remove_ads_lifetime` (غير قابل للتغيير)
- نوع: منتج مُدار / Non-consumable (شراء لمرة واحدة)
- السعر الافتراضي: **1.99 USD** ثم راجع الأسعار المحلية المُقترحة (التطبيق يعرض السعر المحلي الذي تعيده Google Play تلقائياً).
- فعّله (Active). لن يُرجع المتجر تفاصيل المنتج قبل رفع بناء إلى مسار اختبار على الأقل.
- أضف حسابات **License testers** (Settings → License testing) لتجربة الشراء/الإلغاء/الاسترداد بلا دفع حقيقي.

## 4) توقيع التطبيق
```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```
- احفظ الملف وكلمات المرور في مدير كلمات مرور وبنسخة احتياطية آمنة. **لا ترفعها إلى git** (`.gitignore` يمنعها).
- فعّل **Play App Signing** عند أول رفع؛ الملف أعلاه هو «مفتاح الرفع».

## 5) بناء AAB
محلياً:
```bash
export RELEASE_STORE_FILE=/path/upload-keystore.jks RELEASE_STORE_PASSWORD=... RELEASE_KEY_ALIAS=upload RELEASE_KEY_PASSWORD=...
export ADMOB_APP_ID=... ADMOB_BANNER_UNIT_ID=... ADMOB_INTERSTITIAL_UNIT_ID=...
export PRIVACY_POLICY_URL=... TERMS_URL=... CONTACT_EMAIL=...
./gradlew :engine:test :app:testDebugUnitTest :app:bundleRelease
# الناتج: app/build/outputs/bundle/release/app-release.aab
```
أو من GitHub: Actions → *Release bundle* (بعد إضافة الأسرار المذكورة في الملف). الـ build يتوقف إن بقيت معرّفات الاختبار أو نقصت قيمة.
ارفع `versionCode` في كل إصدار جديد.

## 6) مسارات الاختبار
1. **Internal testing:** ارفع الـ AAB، أضف بريدك، ثبّت من رابط الاختبار، واختبر الشراء مع License tester.
2. **Closed testing:** الحسابات الشخصية الجديدة قد تحتاج عدداً أدنى من المختبرين لمدة محددة قبل الإنتاج (كان 12 مختبراً لمدة 14 يوماً عند كتابتي هذا — **تحقق من الشرط الحالي**).
3. بعد نجاح الاختبارات اطلب الوصول للإنتاج.

## 7) قائمة التحقق قبل الإرسال
- [ ] معرّفات AdMob الحقيقية فقط في Release، وإعلانات الاختبار في Debug.
- [ ] اختبار يدوي على جهازين على الأقل (شاشة صغيرة وكبيرة) بالعربية والفرنسية والإنجليزية.
- [ ] البانر لا يغطي الأزرار ولا الحقول (اختبره بإعلانات الاختبار على الجهاز).
- [ ] الإعلان البيني لا يظهر عند «احسب» ولا عند فتح/إغلاق التطبيق، وبفاصل ≥ 180 ث.
- [ ] الشراء: نجاح، إلغاء، دفع معلّق، استعادة بعد إعادة التثبيت، اختفاء البانر والبيني بعد الشراء.
- [ ] نموذج Data Safety وسياسة الخصوصية مطابقان لما يفعله التطبيق.
- [ ] نموذج Data Safety يعكس إصدار Google Mobile Ads SDK المُستخدم.
- [ ] مستوى API المستهدف وإصدار Billing Library يستوفيان شرط Google الحالي.
- [ ] لقطات شاشة حقيقية، أيقونة 512، رسم الميزة، النصوص الثلاثة.
- [ ] التطبيق لا يطلب صلاحيات غير الإنترنت.

## 8) حدود التحقق من الشراء (مهم)
التطبيق يعتمد على حالة الشراء الموقّعة من Google Play داخل الجهاز (`queryPurchasesAsync` + `acknowledgePurchase`) ويخزّن الاستحقاق محلياً ليعمل دون إنترنت،
ويعيد التحقق عند كل اتصال/استئناف (يُسحب الاستحقاق إن أُعيد المبلغ). هذا مناسب لمنتج بسعر 1.99$، لكنه **لا يمنع** مستخدماً ذا جهاز مخترق (root) من تزوير الاستحقاق محلياً.
للحماية الأقوى: أرسل `purchaseToken` إلى خادم يتحقق عبر Google Play Developer API (`purchases.products.get`) — يتطلب خادماً وحساب خدمة، وهو غير مُنفَّذ هنا لأن المشروع بلا خادم عن قصد.
