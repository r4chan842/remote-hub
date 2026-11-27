# Design System — RemoteHub

همهٔ مقادیر این سند مستقیماً در `composeApp/src/commonMain/kotlin/app/remotehub/design/Theme.kt` پیاده شده‌اند و در CI رندر می‌شوند. هیچ مقدار طراحی فقط روی کاغذ نیست.

## ۱. منبع رسمی

| بخش | منبع |
|---|---|
| کامپوننت‌ها | `org.jetbrains.compose.material3` — پیاده‌سازی رسمی Material 3 |
| آیکون‌ها | Material Symbols Outlined، فونت upstream گوگل |
| پین نسخه | `google/material-design-icons @ 10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7` |
| فونت متن | Vazirmatn `@ 6e553e33489a8f9dfaccc76860a2e3f3c1e66de7` |

۸۸ گلیف مورد نیاز از فایل رسمی codepoints استخراج و به‌صورت ثابت در `Symbols.kt` تعریف شده‌اند. هیچ آیکون دست‌ساز، SVG تصادفی یا تصویر تولیدشده استفاده نمی‌شود.

## ۲. توکن‌های رنگ

یک طرح آبی/خاکستری حرفه‌ای با کانتینرهای M3. رنگ‌های معنایی وضعیت جدا از طرح اصلی نگه داشته می‌شوند تا در هر دو تم خوانا بمانند.

### Light
| نقش | مقدار |
|---|---|
| primary | `#2E5AAC` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#D8E2FF` |
| onPrimaryContainer | `#001945` |
| secondary | `#575E71` |
| secondaryContainer | `#DBE2F9` |
| tertiary | `#006A6A` |
| tertiaryContainer | `#9CF1F0` |
| error | `#BA1A1A` |
| errorContainer | `#FFDAD6` |
| surface | `#FAF9FD` |
| surfaceContainerLow | `#F4F3F8` |
| surfaceContainer | `#EEEDF3` |
| surfaceContainerHigh | `#E8E7EE` |
| surfaceContainerHighest | `#E2E2E9` |
| onSurface | `#1A1B20` |
| onSurfaceVariant | `#44474F` |
| outline | `#757780` |
| outlineVariant | `#C5C6D0` |

### Dark
| نقش | مقدار |
|---|---|
| primary | `#ADC6FF` |
| onPrimary | `#002E69` |
| primaryContainer | `#004494` |
| onPrimaryContainer | `#D8E2FF` |
| secondary | `#BFC6DC` |
| secondaryContainer | `#3F4759` |
| tertiary | `#80D5D4` |
| tertiaryContainer | `#004F50` |
| error | `#FFB4AB` |
| errorContainer | `#93000A` |
| surface | `#121318` |
| surfaceContainerLow | `#1A1B20` |
| surfaceContainer | `#1E1F25` |
| surfaceContainerHigh | `#282A2F` |
| surfaceContainerHighest | `#33353A` |
| onSurface | `#E2E2E9` |
| onSurfaceVariant | `#C5C6D0` |
| outline | `#8F9099` |
| outlineVariant | `#44474F` |

### رنگ وضعیت
| وضعیت | Light container | Dark container |
|---|---|---|
| success | `#C8EED2` | `#14522C` |
| warning | `#FFE2B8` | `#5C3A00` |
| neutral | `#E4E2E9` | `#33353A` |

نگاشت وضعیت سرور:
- `Ready` → success
- `Connected` → primaryContainer
- `Provisioning` → tertiaryContainer
- `Expiring` / `Stopping` → warning
- `Failed` → errorContainer
- `Stopped` → neutral

## ۳. تایپوگرافی

خانواده: Vazirmatn در چهار وزن. مقیاس کامل M3:

| نقش | اندازه / ارتفاع | وزن |
|---|---|---|
| displaySmall | 34 / 42 | Normal |
| headlineMedium | 27 / 34 | SemiBold |
| headlineSmall | 23 / 30 | SemiBold |
| titleLarge | 21 / 28 | Medium |
| titleMedium | 16 / 24 | Medium |
| titleSmall | 14 / 20 | Medium |
| bodyLarge | 16 / 24 | Normal |
| bodyMedium | 14 / 21 | Normal |
| bodySmall | 12 / 17 | Normal |
| labelLarge | 14 / 20 | Medium |
| labelMedium | 12 / 16 | Medium |
| labelSmall | 11 / 16 | Medium |

## ۴. Shape

| نقش | شعاع |
|---|---|
| extraSmall | 4dp |
| small | 8dp |
| medium | 12dp |
| large | 16dp |
| extraLarge | 28dp |

کارت‌ها `large`، دکمه‌ها `large`، چیپ‌ها `small`، فیلدها `medium`.

## ۵. کامپوننت‌های مشترک

| کامپوننت | کاربرد |
|---|---|
| `Panel` | کارت سطح `surfaceContainerLow` بدون سایه |
| `SectionTitle` | عنوان بخش با اسلات trailing |
| `StatePill` | وضعیت سرور با نقطهٔ رنگی |
| `TagChip` | برچسب کوتاه |
| `InfoRow` | برچسب/مقدار با دکمهٔ کپی |
| `MetricTile` | عدد بزرگ + توضیح + آیکون |
| `StepRow` | مرحله با وضعیت واقعی و زمان صرف‌شده |
| `SetupRow` | ردیف راه‌اندازی با تیک/فلش |
| `TimeMeter` | نوار زمان باقی‌مانده با حالت هشدار |
| `WarningBanner` / `NoticeBanner` | پیام هشدار و اطلاع |
| `EmptyState` | حالت خالی |
| `KeyValueGrid` | شبکهٔ مشخصات، واکنش‌گرا |

## ۶. ناوبری

| عرض | الگو |
|---|---|
| Compact (تلفن) | `NavigationBar` پایین + `FloatingActionButton` |
| Expanded (دسکتاپ) | `NavigationRail` چپ + FAB گسترده |

مقصدها: داشبورد، سرورها، شبکه، تنظیمات. صفحات ساخت سرور، جزئیات سرور و ریموت دسکتاپ به‌صورت مسیر جدا روی همان مقصد باز می‌شوند. ریموت دسکتاپ تمام‌صفحه و بدون ناوبری است تا فضای تصویر حداکثر بماند.

## ۷. راست‌به‌چپ

`LocalLayoutDirection` از خود جدول زبان تعیین می‌شود. فارسی و عربی RTL، انگلیسی LTR. چون همهٔ چیدمان‌ها از `Row`/`Column` و `Arrangement` استفاده می‌کنند، آینه‌سازی خودکار است و هیچ مختص‌گذاری دستی وجود ندارد.

## ۸. صفحات

| # | صفحه | محتوای کلیدی |
|---|---|---|
| ۰۱ | Dashboard | چک‌لیست راه‌اندازی، سه سنجه، کارت سرورها، سهمیه، اطلاع انطباق |
| ۰۲ | Dashboard خالی | حالت واقعی بدون حساب و بدون سرور |
| ۰۳ | GitHub — Device Code | کد بزرگ، کپی، باز کردن GitHub، انتظار تأیید، شمارش انقضا |
| ۰۴ | GitHub — PAT | حساب متصل، فیلد توکن، مجوزهای موردنیاز، اعتبارسنجی |
| ۰۵ | Network | وضعیت گره داخلی، چیپ‌های قابلیت، مشخصات مسیر، کلید احراز |
| ۰۶ | Create — Target | سرویس‌دهنده، منطقه، ایمیج، اندازه، نام |
| ۰۷ | Create — Config | آستانهٔ هشدار، بکاپ خودکار، رمزنگاری، هشدار ریپوی عمومی |
| ۰۸ | Create — Progress | هشت مرحله با وضعیت واقعی و زمان |
| ۰۹ | Server Detail | زمان باقی‌مانده، اطلاعات اتصال، چرخه حیات، مشخصات، بکاپ‌ها، تاریخچه، ورک‌فلو |
| ۱۰ | Remote Desktop | نوار جلسه، سطح فریم‌بافر، نوار ابزار لمس/کیبورد/کلیپ‌بورد |
| ۱۱ | Settings | تم، زبان، اعلان، امنیت، راهنماها، درباره |

## ۹. ماتریس رندر

هر صفحه در چهار حالت رندر می‌شود:

```
fa-dark-phone      824 × 1830 px   (412 × 915 dp @2x)
fa-dark-desktop   1920 × 1320 px   (1280 × 880 dp @1.5x)
en-light-phone     824 × 1830 px
en-light-desktop  1920 × 1320 px
```

۱۱ صفحه × ۴ حالت = **۴۴ PNG**. CI تعداد و حداقل حجم هر فایل را بررسی می‌کند؛ خروجی خالی یا خراب باعث شکست بیلد می‌شود.

## ۱۰. آنچه استفاده نمی‌شود

- WebView برای UI
- HTML/CSS برای صفحات
- تصویر تولیدشده یا گرافیک تزئینی
- گرادیان‌های تزئینی و سایه‌های سنگین
- انیمیشن پرهزینه در حالت بی‌کار
- درصد پیشرفت ساختگی
