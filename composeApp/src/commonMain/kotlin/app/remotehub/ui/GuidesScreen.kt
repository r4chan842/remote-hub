package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

data class GuideSection(val title: String, val body: String)

@Composable
fun SettingsGuides(guideId: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val sections = guideSections(guideId, s)
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sections.forEach { section ->
            Panel {
                SectionTitle(section.title)
                Text(
                    section.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun guideSections(guideId: String, s: AppStrings): List<GuideSection> = when (s.tag) {
    "en" -> englishGuides(guideId, s)
    "ar" -> arabicGuides(guideId, s)
    else -> persianGuides(guideId, s)
}

private fun persianGuides(id: String, s: AppStrings): List<GuideSection> = when (id) {
    "start" -> listOf(
        GuideSection("۱. اتصال به GitHub", "از تنظیمات، حساب GitHub را وصل کنید. روش «کد دستگاه» ساده‌ترین راه است: کد نمایش‌داده‌شده را کپی کنید، صفحهٔ رسمی GitHub را باز کنید، کد را وارد کنید و دسترسی را تأیید کنید. اگر توکن شخصی دارید، از تب دوم استفاده کنید؛ توکن باید دسترسی repo و workflow داشته باشد. توکن فقط در Keystore سخت‌افزاری اندروید رمزنگاری و نگهداری می‌شود."),
        GuideSection("۲. شبکهٔ خصوصی", "در بخش شبکه، کلید احراز هویت Tailscale را وارد کنید. کلید را از پنل Tailscale در بخش Settings → Keys بسازید و گزینهٔ Ephemeral را روشن بگذارید تا گره پس از پایان نشست خودکار حذف شود. موتور Tailscale داخل همین برنامه و در حالت userspace اجرا می‌شود؛ نه root لازم است، نه VPN سیستم را قطع می‌کند و نه برنامهٔ جداگانه‌ای باید نصب کنید."),
        GuideSection("۳. سرویس‌دهندهٔ سرور", "برای ساخت سرور واقعی، توکن API سرویس‌دهندهٔ ابری خود را وارد کنید. Hetzner Cloud و DigitalOcean پشتیبانی می‌شوند. سرور روی حساب خودتان ساخته می‌شود، پس هزینه و مسئولیت آن با شماست و هیچ محدودیتی دور زده نمی‌شود."),
        GuideSection("۴. ساخت سرور", "دکمهٔ «سرور جدید» را بزنید، نام، منطقه، اندازه و سیستم‌عامل را انتخاب کنید و هشدار پایان زمان را تنظیم کنید. برنامه ریپازیتوری را می‌سازد، Secrets را با کلید عمومی خود ریپو رمزنگاری می‌کند، ورک‌فلو را ثبت و اجرا می‌کند و مراحل را از API واقعی GitHub نشان می‌دهد. هیچ درصد ساختگی وجود ندارد."),
        GuideSection("۵. ریموت دسکتاپ", "پس از آماده شدن، «ریموت دسکتاپ» را بزنید. تصویر با موتور بومی FreeRDP روی بستر Tailscale رمزنگاری‌شده منتقل می‌شود. حالت لمس مستقیم برای کلیک دقیق و حالت ترک‌پد برای جابه‌جایی نشانگر مناسب است. کیبورد، کلیپ‌بورد دوطرفه و Ctrl+Alt+Del در نوار پایین در دسترس هستند."),
        GuideSection("۶. پایان نشست", "پیش از پایان زمان، هشدار دریافت می‌کنید. با «ایجاد بکاپ» داده روی همان ریپازیتوری خودتان و به‌صورت رمزنگاری‌شده ذخیره می‌شود؛ کلید رمز هرگز آپلود نمی‌شود. «توقف» سرور را حذف می‌کند و هزینه را قطع می‌کند."),
    )
    "troubleshoot" -> listOf(
        GuideSection("ورود GitHub ناموفق", "اگر کد دستگاه منقضی شد، دوباره صفحه را باز کنید تا کد تازه بگیرید. اگر توکن شخصی خطای ۴۰ داد، توکن باطل شده است؛ توکن جدید بسازید. خطای ۴۰۳ یعنی دسترسی repo یا workflow کم است."),
        GuideSection("شبکه وصل نمی‌شود", "کلید احراز هویت باید معتبر و مصرف‌نشده باشد. کلیدهای Ephemeral یک‌بارمصرف‌اند. اگر گوشی VPN فعال دارد، مشکلی نیست چون این برنامه از VpnService استفاده نمی‌کند. با تغییر شبکهٔ گوشی، وضعیت خودکار بازخوانی می‌شود."),
        GuideSection("ورک‌فلو اجرا نمی‌شود", "مطمئن شوید Actions روی ریپازیتوری فعال است و توکن دسترسی workflow دارد. اگر ورک‌فلو در صف ماند، سهمیهٔ Actions حساب پر شده است."),
        GuideSection("سرور ساخته نشد", "توکن سرویس‌دهنده باید دسترسی نوشتن داشته باشد و حساب شما موجودی یا اعتبار کافی داشته باشد. اگر منطقه یا اندازه نامعتبر باشد، API خطا برمی‌گرداند و متن خطا در جزئیات سرور نمایش داده می‌شود."),
        GuideSection("ریموت وصل نمی‌شود", "ابتدا باید شبکهٔ خصوصی آنلاین باشد. سپس گرهٔ سرور باید در tailnet دیده شود. اگر گواهی سرور عوض شده باشد، اتصال عمداً قطع می‌شود؛ این محافظت در برابر حملهٔ میانی است. سرور را بازسازی کنید تا گواهی تازه پین شود."),
        GuideSection("تصویر کند است", "کیفیت را پایین بیاورید تا پهنای باند کم شود. حالت ترک‌پد ترافیک کمتری از لمس مستقیم دارد. تأخیر بالای ۱۵۰ میلی‌ثانیه معمولاً یعنی مسیر از رله عبور می‌کند؛ در جزئیات شبکه، مسیر مستقیم را بررسی کنید."),
    )
    else -> listOf(
        GuideSection("مرز استفاده از GitHub Actions", "GitHub Actions در این برنامه فقط برای ساخت، تست، انتشار و هماهنگ‌سازی کوتاه‌مدت استفاده می‌شود. هر جاب حداکثر بیست دقیقه طول می‌کشد و بعد تمام می‌شود. خودِ دسکتاپ ریموت هرگز روی رانر گیت‌هاب اجرا نمی‌شود."),
        GuideSection("زیرساخت ریموت کجاست", "سرور روی حساب ابری خودتان ساخته می‌شود؛ ماشین واقعی با پردازنده و حافظهٔ مشخص که در جزئیات سرور می‌بینید. این کار مجاز است چون منابع متعلق به شماست و هیچ سهمیه‌ای دور زده نمی‌شود."),
        GuideSection("محدودیت‌های واقعی", "هر جاب Actions روی رانر میزبان حداکثر شش ساعت است، اما ورک‌فلوی این برنامه بیست دقیقه سقف دارد. حساب رایگان برای ریپازیتوری خصوصی دویست دقیقه در ماه دارد و برای ریپازیتوری عمومی نامحدود است. عمر سرور را خودتان با پارامتر دقیقه تعیین می‌کنید و پس از آن سرور خودکار خاموش می‌شود."),
        GuideSection("حریم خصوصی", "توکن‌ها در Keystore اندروید نگهداری می‌شوند. Secrets با کلید عمومی خود ریپازیتوری رمزنگاری می‌شوند، پس فقط رانر شما می‌تواند آن‌ها را باز کند. بکاپ‌ها پیش از آپلود رمزنگاری می‌شوند و کلید هرگز از گوشی خارج نمی‌شود. در لاگ‌ها، کلیدها و گذرواژه‌ها پیش از آپلود حذف می‌شوند."),
        GuideSection("مجوزهای برنامه", "اینترنت و وضعیت شبکه برای اتصال، اعلان برای هشدار پایان زمان، زیست‌سنجی برای قفل اختیاری و سرویس پیش‌زمینه برای نشست فعال. هیچ مجوز موقعیت، مخاطبین، دوربین یا حافظهٔ عمومی درخواست نمی‌شود."),
        GuideSection("مسئولیت هزینه", "سرور روی حساب شما ساخته می‌شود و هزینه دارد. دکمهٔ توقف سرور را حذف می‌کند. اگر نشست را رها کنید، زمان‌سنج خودکار سرور را خاموش می‌کند، اما همیشه پس از پایان کار وضعیت را در داشبورد بررسی کنید."),
    )
}

private fun englishGuides(id: String, s: AppStrings): List<GuideSection> = when (id) {
    "start" -> listOf(
        GuideSection("1. Connect GitHub", "Open Settings and connect your GitHub account. Device code is the simplest path: copy the code, open the official GitHub page, enter it and approve. If you already own a personal access token, use the second tab; it needs repo and workflow scopes. The token is encrypted inside the Android hardware Keystore and never leaves the device in plain text."),
        GuideSection("2. Private network", "In Network, paste a Tailscale auth key. Create it in the Tailscale admin console under Settings then Keys, and leave Ephemeral enabled so the node disappears when the session ends. The Tailscale engine runs inside this app in userspace mode: no root, no separate client, and your existing system VPN keeps working."),
        GuideSection("3. Cloud provider", "To build a real server, add your own cloud API token. Hetzner Cloud and DigitalOcean are supported. The machine is created in your own account, so billing and responsibility stay with you and no platform quota is bypassed."),
        GuideSection("4. Create a server", "Tap New server, pick a name, region, size and image, then set the expiry warning. The app ensures the repository exists, seals secrets with that repository's own public key, commits and dispatches the workflow, and reports every stage from the real GitHub API. No synthetic progress is shown."),
        GuideSection("5. Remote desktop", "Once ready, tap Remote desktop. Frames travel through the native FreeRDP engine over the encrypted Tailscale path. Direct touch is best for precise clicks; trackpad mode is better for moving the pointer. Keyboard, two-way clipboard and Ctrl+Alt+Del live in the bottom bar."),
        GuideSection("6. Ending a session", "You get a warning before the window closes. Create backup stores data in your own repository, encrypted on the device; the key is never uploaded. Stop deletes the server and ends billing."),
    )
    "troubleshoot" -> listOf(
        GuideSection("GitHub sign-in fails", "If the device code expired, reopen the page to get a fresh one. A 401 on a personal token means it was revoked; create a new one. A 403 means repo or workflow scope is missing."),
        GuideSection("Network will not connect", "The auth key must be valid and unused; ephemeral keys are single use. An active phone VPN is fine because this app never calls VpnService. Switching networks triggers an automatic re-read of the interface state."),
        GuideSection("Workflow does not start", "Check that Actions is enabled on the repository and that the token carries the workflow scope. A queued run usually means the account Actions allowance is exhausted."),
        GuideSection("Server creation fails", "The provider token needs write access and the account needs credit. An invalid region or size is rejected by the provider API and the exact message appears in server details."),
        GuideSection("Remote desktop will not connect", "The private network must be online first, then the server node must appear in the tailnet. If the host certificate changed, the session is deliberately aborted as a man-in-the-middle guard; rebuild the server to pin a fresh certificate."),
        GuideSection("Video feels slow", "Lower the quality to reduce bandwidth. Trackpad mode sends less traffic than direct touch. Latency above 150 ms usually means the path goes through a relay; check the transport row in Network details."),
    )
    else -> listOf(
        GuideSection("How GitHub Actions is used", "Actions only builds, tests, releases and performs short orchestration. Each job is capped at twenty minutes and then exits. The remote desktop itself never runs on a GitHub-hosted runner."),
        GuideSection("Where the remote machine lives", "The server is created in your own cloud account: a real machine with the CPU and memory shown in server details. That is permitted because the resources belong to you and no quota is circumvented."),
        GuideSection("Real limits", "A GitHub-hosted job can run at most six hours, but this workflow caps itself at twenty minutes. Free accounts get two hundred minutes per month for private repositories and unlimited minutes for public ones. Server lifetime is the minute value you choose, after which the host shuts itself down."),
        GuideSection("Privacy", "Tokens live in the Android Keystore. Secrets are sealed with the repository's own public key so only your runner can open them. Backups are encrypted before upload and the key never leaves the phone. Keys and passwords are stripped from diagnostics before upload."),
        GuideSection("Permissions", "Internet and network state for connectivity, notifications for expiry alerts, biometrics for the optional lock, and a foreground service for the active session. No location, contacts, camera or shared storage permission is requested."),
        GuideSection("Billing responsibility", "The server is created in your account and costs money. Stop deletes it. The host also shuts itself down when the timer elapses, but always confirm the state on the dashboard when you finish."),
    )
}

private fun arabicGuides(id: String, s: AppStrings): List<GuideSection> = when (id) {
    "start" -> listOf(
        GuideSection("١. ربط GitHub", "افتح الإعدادات واربط حساب GitHub. طريقة رمز الجهاز هي الأسهل: انسخ الرمز، افتح صفحة GitHub الرسمية، أدخل الرمز ووافق على الوصول. إذا كان لديك رمز شخصي فاستخدم التبويب الثاني؛ يجب أن يملك صلاحيتي repo و workflow. يُشفَّر الرمز داخل Keystore العتادي في أندرويد ولا يغادر الجهاز كنص صريح."),
        GuideSection("٢. الشبكة الخاصة", "في قسم الشبكة أدخل مفتاح مصادقة Tailscale. أنشئه من لوحة Tailscale في Settings ثم Keys واترك خيار Ephemeral مفعلاً ليُحذف الجهاز تلقائياً عند انتهاء الجلسة. يعمل محرك Tailscale داخل هذا التطبيق في وضع userspace: لا يحتاج root ولا يقطع VPN النظام ولا يتطلب تطبيقاً منفصلاً."),
        GuideSection("٣. مزوّد الخادم", "لإنشاء خادم حقيقي أضف رمز API الخاص بحسابك السحابي. يُدعم Hetzner Cloud و DigitalOcean. يُنشأ الخادم في حسابك أنت، فتبقى التكلفة والمسؤولية عليك ولا يتم تجاوز أي حصة."),
        GuideSection("٤. إنشاء الخادم", "اضغط خادم جديد واختر الاسم والمنطقة والحجم ونظام التشغيل ثم اضبط تنبيه انتهاء الوقت. يتأكد التطبيق من وجود المستودع، ويشفّر الأسرار بالمفتاح العام للمستودع نفسه، ثم يسجّل سير العمل ويشغّله ويعرض كل مرحلة من واجهة GitHub الحقيقية دون أي نسبة مصطنعة."),
        GuideSection("٥. سطح المكتب البعيد", "عند الجهوزية اضغط سطح المكتب البعيد. تنتقل الإطارات عبر محرك FreeRDP الأصلي فوق مسار Tailscale المشفّر. اللمس المباشر أدق للنقر، ووضع لوحة التتبع أفضل لتحريك المؤشر. لوحة المفاتيح والحافظة ثنائية الاتجاه و Ctrl+Alt+Del في الشريط السفلي."),
        GuideSection("٦. إنهاء الجلسة", "يصلك تنبيه قبل انتهاء النافذة الزمنية. إنشاء نسخة احتياطية يخزّن البيانات في مستودعك نفسه مشفّرة على الجهاز، ولا يُرفع المفتاح أبداً. الإيقاف يحذف الخادم ويوقف الفوترة."),
    )
    "troubleshoot" -> listOf(
        GuideSection("فشل تسجيل الدخول", "إذا انتهت صلاحية رمز الجهاز فأعد فتح الصفحة للحصول على رمز جديد. الخطأ 401 للرمز الشخصي يعني أنه أُلغي؛ أنشئ رمزاً جديداً. الخطأ 403 يعني نقص صلاحية repo أو workflow."),
        GuideSection("الشبكة لا تتصل", "يجب أن يكون مفتاح المصادقة صالحاً وغير مستخدم؛ مفاتيح Ephemeral تُستخدم مرة واحدة. وجود VPN نشط على الهاتف لا يمنع العمل لأن التطبيق لا يستدعي VpnService. تغيير الشبكة يعيد قراءة حالة الواجهة تلقائياً."),
        GuideSection("سير العمل لا يبدأ", "تأكد أن Actions مفعّل على المستودع وأن الرمز يملك صلاحية workflow. بقاء التشغيل في الطابور يعني عادة نفاد حصة Actions."),
        GuideSection("فشل إنشاء الخادم", "يحتاج رمز المزوّد صلاحية كتابة ويحتاج الحساب رصيداً. المنطقة أو الحجم غير الصالح يُرفض من واجهة المزوّد ويظهر نص الخطأ في تفاصيل الخادم."),
        GuideSection("سطح المكتب لا يتصل", "يجب أن تكون الشبكة الخاصة متصلة أولاً ثم يظهر عقدة الخادم في الشبكة. إذا تغيّرت شهادة المضيف تُقطع الجلسة عمداً كحماية من الوسيط؛ أعد بناء الخادم لتثبيت شهادة جديدة."),
        GuideSection("الصورة بطيئة", "خفّض الجودة لتقليل عرض النطاق. وضع لوحة التتبع يرسل بيانات أقل من اللمس المباشر. التأخير فوق 150 مللي ثانية يعني غالباً المرور عبر مُرحّل؛ راجع سطر المسار في تفاصيل الشبكة."),
    )
    else -> listOf(
        GuideSection("استخدام GitHub Actions", "يُستخدم Actions للبناء والاختبار والإصدار والتنسيق القصير فقط. كل مهمة محدودة بعشرين دقيقة ثم تنتهي. سطح المكتب البعيد نفسه لا يعمل أبداً على عدّاء يستضيفه GitHub."),
        GuideSection("مكان الجهاز البعيد", "يُنشأ الخادم في حسابك السحابي: جهاز حقيقي بالمعالج والذاكرة الظاهرين في التفاصيل. هذا مسموح لأن الموارد ملكك ولا يتم تجاوز أي حصة."),
        GuideSection("الحدود الحقيقية", "أقصى مدة لمهمة على عدّاء مستضاف ست ساعات، لكن سير العمل هنا يحدّ نفسه بعشرين دقيقة. الحساب المجاني يمنح مئتي دقيقة شهرياً للمستودعات الخاصة وغير محدودة للعامة. عمر الخادم هو عدد الدقائق الذي تختاره ثم يُغلق المضيف نفسه."),
        GuideSection("الخصوصية", "تُحفظ الرموز في Keystore. تُشفّر الأسرار بالمفتاح العام للمستودع فلا يفتحها إلا عدّاؤك. تُشفّر النسخ الاحتياطية قبل الرفع ولا يغادر المفتاح الهاتف. تُزال المفاتيح وكلمات المرور من التشخيصات قبل الرفع."),
        GuideSection("الأذونات", "الإنترنت وحالة الشبكة للاتصال، والإشعارات لتنبيه الانتهاء، والقياسات الحيوية للقفل الاختياري، وخدمة أمامية للجلسة النشطة. لا يُطلب إذن الموقع أو جهات الاتصال أو الكاميرا أو التخزين العام."),
        GuideSection("مسؤولية الفوترة", "يُنشأ الخادم في حسابك وله تكلفة. الإيقاف يحذفه. يُغلق المضيف نفسه عند انتهاء المؤقت، لكن تحقق دائماً من الحالة في لوحة المعلومات بعد الانتهاء."),
    )
}
