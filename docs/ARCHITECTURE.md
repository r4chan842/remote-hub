# معماری RemoteHub

نسخهٔ ۱ — تأییدشده در GitHub Actions.

## ۱. تصمیم استک

طبق اصلاح کارفرما، **Flutter حذف شد**. رابط کاربری وب، WebView و HTML به‌طور کامل از پروژه کنار گذاشته شده‌اند.

| لایه | فناوری انتخاب‌شده | دلیل |
|---|---|---|
| زبان مشترک | Kotlin 2.1.21 | یک زبان برای Android و Windows، بدون پل زبانی اضافه |
| رابط کاربری | Compose Multiplatform 1.8.2 | همان کامپوننت‌های رسمی Material 3 روی هر دو پلتفرم |
| طراحی | Material 3 رسمی (`compose.material3`) | توکن‌های رنگ، تایپوگرافی، Shape و کامپوننت استاندارد |
| آیکون | Material Symbols Outlined رسمی | فونت upstream با کامیت پین‌شده، ۸۸ گلیف استخراج‌شده |
| فونت متن | Vazirmatn | پوشش کامل فارسی/عربی/لاتین، رندر درست RTL |
| شبکه | Go + `tsnet` (Userspace) | بدون Root، بدون VpnService، بدون کلاینت جداگانه |
| ریموت دسکتاپ | FreeRDP 3 (بومی) | کمترین تأخیر و مصرف، بدون WebView |
| ذخیرهٔ امن | Android Keystore / Windows DPAPI | کلید سخت‌افزاری/سیستمی، بدون کلید در کد |
| CI/CD | GitHub Actions | تست، رندر، بیلد و انتشار — هیچ ابزار lokal لازم نیست |

### چرا Compose Multiplatform و نه دو اپ جدا
یک منبع UI برای هر دو پلتفرم، یعنی طراحی یک‌بار پیاده و یک‌بار تست می‌شود. تنها بخش‌های پلتفرمی، اتصال به موتور بومی هستند که پشت `expect/actual` قرار می‌گیرند.

## ۲. لایه‌بندی

```
Presentation  (composeApp/src/commonMain/.../ui)
    ↓ state in / events out
Application   (use cases، orchestration، state machine)
    ↓
Domain        (composeApp/src/commonMain/.../domain)  ← بدون وابستگی به UI یا پلتفرم
    ↑
Infrastructure(GitHub API، Tailscale API، SecureStorage، Backup)
    ↑
Network Core  (Go tsnet)      Remote Desktop (FreeRDP)
```

قاعدهٔ وابستگی: `ui` هرگز مستقیم به شبکه یا فایل دسترسی ندارد. هر صفحه یک تابع خالص از state است و رویدادها را به بالا می‌فرستد. به همین دلیل می‌توان تمام صفحات را بدون هیچ سرور واقعی رندر و تست کرد.

## ۳. ماژول‌ها

| ماژول | مسیر | وضعیت |
|---|---|---|
| Design System | `design/Theme.kt`, `design/Symbols.kt`, `design/Components.kt` | پیاده‌سازی شده |
| Domain | `domain/Models.kt` | پیاده‌سازی شده |
| Localization | `i18n/Strings.kt` | پیاده‌سازی شده (fa/en/ar) |
| Presentation | `ui/*.kt` | پیاده‌سازی شده |
| Authentication | `auth/` | فاز بعد |
| GitHub Integration | `github/` | فاز بعد |
| Server Management | `server/` | فاز بعد |
| Network Core | `native/tscore` (Go) | فاز بعد |
| Remote Desktop | `native/rdp` (FreeRDP) | فاز بعد |

## ۴. پل Kotlin ↔ Go

سبک‌ترین روش قابل نگهداری انتخاب شد: **یک کتابخانهٔ C-ABI با JSON پیام**، نه gRPC و نه چند پل موازی.

```
Kotlin (common)  ── expect/actual ──►  JNI (Android) / JNI (Desktop JVM)
                                            │
                                            ▼
                                   libtscore (Go, c-shared)
                                   start / stop / status / dial / mintKey
```

- Android: `gomobile bind` → `.aar`
- Windows: `go build -buildmode=c-shared` → `.dll`
- پیام‌ها JSON ساده هستند؛ هیچ فریم‌ورک سریال‌سازی سنگینی اضافه نمی‌شود.
- وضعیت شبکه با polling کم‌هزینه فقط وقتی صفحهٔ شبکه باز است خوانده می‌شود.

### چرا Userspace
`tsnet` در حالت userspace هیچ اینترفیس TUN نمی‌سازد. نتیجه:

- روی اندروید `VpnService` اشغال نمی‌شود، پس VPN فعلی کاربر همچنان کار می‌کند.
- Root لازم نیست.
- روی ویندوز نصب سرویس یا دسترسی Administrator لازم نیست.
- فقط ترافیک خود اپ از تونل عبور می‌کند، نه کل ترافیک دستگاه.

اتصال RDP از یک forwarder محلی روی `127.0.0.1` عبور می‌کند که Go آن را به آدرس Tailscale مقصد وصل می‌کند. در نتیجه موتور RDP هیچ آگاهی از Tailscale ندارد و مستقل تست می‌شود.

## ۵. احراز هویت GitHub

### روش اول — Device Authorization Flow
```
POST /login/device/code        → device_code + user_code + interval
نمایش user_code + دکمه کپی
باز کردن github.com/login/device در مرورگر سیستم
POST /login/oauth/access_token → authorization_pending | slow_down | access_token
ذخیره در Keystore / DPAPI
```
این روش به `client_secret` نیاز ندارد، پس برای اپ توزیع‌شده امن است. هیچ توکنی از WebView استخراج نمی‌شود.

### روش دوم — Personal Access Token
```
دریافت توکن → GET /user → بررسی X-OAuth-Scopes
نمایش مجوزهای موجود و ناقص → ذخیرهٔ امن
```
مجوزهای حداقلی: `repo`, `workflow`, `read:user`. برای ریپازیتوری فقط عمومی، `public_repo` کافی است.

### چرا استخراج توکن از WebView انجام نمی‌شود
گوگل OAuth را در WebView مسدود می‌کند (`disallowed_useragent`). خواندن کوکی یا توکن از WebView هم شکننده است و هم مرز امنیتی را می‌شکند. این روش در پروژه استفاده نمی‌شود.

## ۶. Repository و Workflow

ترتیب عملیات، با پیشرفت واقعی از API:

| # | عملیات | API |
|---|---|---|
| ۱ | بررسی حساب | `GET /user` |
| ۲ | بررسی/ساخت ریپازیتوری | `GET/POST /user/repos` |
| ۳ | ثبت Secrets | `GET .../actions/secrets/public-key` + `PUT .../actions/secrets/{name}` |
| ۴ | اجرای ورک‌فلو | `POST .../actions/workflows/{id}/dispatches` |
| ۵ | وضعیت اجرا | `GET .../actions/runs/{id}` و `.../jobs` |
| ۶ | دریافت اطلاعات اتصال | Tailscale API + خروجی رمزنگاری‌شده |
| ۷ | تحویل | نمایش در UI |

Secrets با **libsodium sealed box** و کلید عمومی خود ریپازیتوری رمز می‌شوند. هیچ secretی در لاگ چاپ نمی‌شود. چون ریپازیتوری عمومی است، لاگ‌ها عمومی‌اند؛ بنابراین گذرواژهٔ RDP توسط اپ تولید و فقط به‌صورت secret ارسال می‌شود و هرگز در خروجی workflow نمی‌آید.

کنترل‌های امنیتی workflow:
- `permissions` حداقلی و صریح در سطح job
- پین کردن اکشن‌ها به SHA کامل
- `workflow_dispatch` فقط از شاخهٔ پیش‌فرض
- اعتبارسنجی ورودی‌ها پیش از dispatch
- بدون obfuscation یا نام‌گذاری فریبنده

## ۷. چرخه حیات سرور

```
Idle → Verifying → EnsuringRepo → StoringSecrets → Dispatching
     → WaitingForRun → JoiningNetwork → Handshake → Ready
Ready → Connected → Expiring → Stopping → Stopped
any → Failed → (Retry | Rebuild)
```

هر گذار از یک پاسخ واقعی API یا یک رویداد شبکه می‌آید. درصد پیشرفت از `تعداد مراحل Done ÷ کل مراحل` محاسبه می‌شود، نه از تایمر.

### هشدار پایان زمان
- مبدأ زمان، زمان واقعی شروع job است.
- آستانهٔ هشدار قابل تنظیم (۱۰/۱۵/۳۰/۶۰ دقیقه).
- اعلان محلی: Android Notification، Windows Toast.
- در آستانه، وضعیت سرور به `Expiring` می‌رود و بنر هشدار و پیشنهاد بکاپ نمایش داده می‌شود.

### اجرای دوباره
`POST .../actions/runs/{id}/rerun` یا dispatch جدید. اپ هشدار می‌دهد که آدرس و گذرواژه عوض می‌شوند و دادهٔ ماندگار روی ماشین قبلی باقی نمی‌ماند.

## ۸. بکاپ روی همان ریپازیتوری

چون ریپازیتوری عمومی است، بکاپ خام هرگز آپلود نمی‌شود.

```
جمع‌آوری فایل‌ها → آرشیو → رمزنگاری AES-256-GCM
کلید از عبارت عبور کاربر با Argon2id → فقط روی دستگاه
آپلود به backups/<server>/<timestamp>.enc در همان ریپو
کلید هرگز آپلود نمی‌شود
```

بازیابی: دانلود blob → باز کردن رمز روی دستگاه → بازگردانی. متادیتای قابل مشاهده فقط نام، اندازه و هش است.

## ۹. انتخاب موتور Remote Desktop

| گزینه | تأخیر | RAM | پیچیدگی | نتیجه |
|---|---|---|---|---|
| FreeRDP بومی + Texture | کم | کم | متوسط | **انتخاب شد** |
| Guacamole / guacd | متوسط | زیاد (سرور جانبی) | زیاد | رد — نیاز به سرویس اضافه |
| noVNC در WebView | زیاد | زیاد | متوسط | رد — WebView و مصرف بالا |
| WebRTC | کم | متوسط | خیلی زیاد | رد — پیچیدگی بی‌دلیل |
| RDP ActiveX ویندوز | کم | کم | زیاد در Compose | رد — فقط ویندوز، اتصال سخت |

FreeRDP یک کتابخانهٔ C است؛ فریم‌ها در یک بافر RGBA رندر و از طریق Texture به Compose داده می‌شوند. ورودی لمس/کیبورد از Compose به موتور می‌رود. هیچ WebView در مسیر تصویر نیست.

قابلیت‌های هدف: NLA/TLS، pinning گواهی، کلیپ‌بورد دوطرفه، حالت لمس مستقیم و ترک‌پد، اتصال مجدد، تنظیم کیفیت.

## ۱۰. امنیت

- توکن‌ها فقط در Keystore/DPAPI؛ هرگز در لاگ، اسکرین‌شات یا بکاپ.
- Auth Keyهای Tailscale کوتاه‌عمر و ephemeral.
- ACL: پورت ۳۳ فقط از دستگاه‌های مجاز همان tailnet.
- گواهی RDP با TOFU و pinning per-host.
- قفل زیست‌سنجی اختیاری برای باز کردن اپ.
- پاک‌سازی کامل اعتبارنامه‌ها با یک دکمه.

## ۱۱. بودجهٔ عملکرد

| شاخص | هدف |
|---|---|
| RAM در حالت بی‌کار | زیر ۱۲۰ MB |
| CPU در حالت بی‌کار | نزدیک صفر (بدون polling دائمی) |
| اندازهٔ APK | کنترل‌شده با subset کردن فونت آیکون در فاز بسته‌بندی |
| راه‌اندازی سرد | زیر ۲ ثانیه تا اولین فریم |
| شبکه | ETag و backoff؛ بدون درخواست تکراری |
| آزادسازی | بستن صحنهٔ RDP و گرهٔ tsnet هنگام خروج |

## ۱۲. تست و انتشار — همه در GitHub

هیچ چیزی روی محیط توسعه‌دهنده نصب نمی‌شود.

| مرحله | فرمان | خروجی |
|---|---|---|
| تست واحد | `gradle :composeApp:desktopTest` | گزارش تست |
| رندر صفحات | `gradle :composeApp:renderScreens` | ۴۴ PNG |
| اعتبارسنجی رندر | بررسی تعداد و حداقل حجم فایل | جلوگیری از خروجی خالی |
| بیلد Android | `gradle :composeApp:assembleRelease` | APK/AAB |
| بیلد Windows | `gradle :composeApp:packageMsi` | MSI |
| انتشار | تگ `vX.Y.Z` → GitHub Release | فایل‌های نصبی |

رندر صفحات یک تست واقعی است: اگر یک کامپوننت بشکند، فونت بارگذاری نشود یا RTL خراب شود، تصویر خروجی خراب یا خالی می‌شود و CI شکست می‌خورد.

## ۱۳. فازهای بعدی

۱. ماژول احراز هویت واقعی GitHub + ذخیرهٔ امن
۲. هستهٔ Go `tsnet` + پل JNI
۳. ماژول Repository/Workflow/Secrets
۴. ماژول مدیریت سرور + اعلان پایان زمان
۵. موتور FreeRDP + نمایش Texture
۶. بکاپ رمزنگاری‌شده
۷. هدف Android، بسته‌بندی Windows، انتشار با تگ
