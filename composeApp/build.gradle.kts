import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    // AGP must be applied before the Kotlin Multiplatform plugin: the KMP plugin
    // registers the Android resource publication action as soon as it is applied
    // and queries AndroidGradlePluginVersion.getCurrent() at that moment.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(files("libs/tsbridge.aar"))
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.lazysodium.android)
            implementation(libs.jna)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.lazysodium.java)
            implementation(libs.jna)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

val releaseAbis = listOf("arm64-v8a", "armeabi-v7a")
// versionCode = base * 10 + ABI digit, so every split APK has its own code and
// arm64 wins over armv7 on devices that support both. Universal keeps digit 0.
val abiVersionDigit = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)

android {
    namespace = "app.remotehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.remotehub"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
        // Optional built-in GitHub OAuth App (Device Flow enabled). Empty = PAT sign-in is the default.
        // ABI selection lives in `splits.abi` below (AGP rejects ndk.abiFilters together
        // with ABI splits). Other ABIs coming from dependencies are dropped in `packaging`.
    }

    // One APK per ABI halves the download for every user; the universal APK stays
    // available as a fallback (-PuniversalApk=false skips it).
    splits {
        abi {
            isEnable = true
            reset()
            include(*releaseAbis.toTypedArray())
            isUniversalApk = (project.findProperty("universalApk") as String?)?.toBoolean() ?: true
        }
    }

    // Google Play dependency metadata is useless for GitHub-distributed APKs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RH_KEYSTORE_FILE")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RH_KEY_ALIAS")
                keyPassword = System.getenv("RH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksPath = System.getenv("RH_KEYSTORE_FILE")
            signingConfig = if (ksPath != null && file(ksPath).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/androidMain/jniLibs")
        }
    }

    packaging {
        // Native libraries stay STORED (uncompressed) and 16 KB zip-aligned so the
        // loader maps them straight from the APK on 4 KB and 16 KB page devices;
        // nothing is extracted to /data at install time.
        // Compressed native libraries: the per-ABI download drops from ~37 MB to ~16 MB.
        // They are extracted once at install time, which every Android version supports.
        jniLibs.useLegacyPackaging = true
        jniLibs.excludes += setOf(
            "lib/x86/**",
            "lib/x86_64/**",
            "lib/armeabi/**",
            "lib/mips/**",
            "lib/mips64/**",
            "lib/riscv64/**",
        )
        // JNA's jar carries desktop dispatch libraries (AIX, macOS, Windows, BSD, Linux,
        // Solaris) that Android can never load. Android-specific JNA natives, if any
        // dependency brings them, are not touched.
        resources.excludes += setOf(
            "com/sun/jna/aix-*/**",
            "com/sun/jna/darwin*/**",
            "com/sun/jna/win32-*/**",
            "com/sun/jna/sunos-*/**",
            "com/sun/jna/freebsd-*/**",
            "com/sun/jna/openbsd-*/**",
            "com/sun/jna/dragonflybsd-*/**",
            "com/sun/jna/linux-*/**",

            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
        )
    }

    buildFeatures { buildConfig = true }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
            val base = output.versionCode.orNull ?: 1
            output.versionCode.set(base * 10 + (abiVersionDigit[abi] ?: 0))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.remotehub.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "app.remotehub.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RemoteHub"
            packageVersion = "1.0.0"
        }
    }
}

val jvmCompilation = (kotlin.targets.getByName("desktop") as KotlinJvmTarget)
    .compilations.getByName("main")

tasks.register<JavaExec>("renderScreens") {
    group = "verification"
    description = "Renders every application screen to PNG from the real Compose Material 3 implementation."
    dependsOn(jvmCompilation.compileTaskProvider)
    classpath = jvmCompilation.output.allOutputs + jvmCompilation.runtimeDependencyFiles
    mainClass.set("app.remotehub.tools.RenderScreensKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
    doFirst {
        val out = layout.buildDirectory.dir("screens").get().asFile
        out.mkdirs()
        args = listOf(out.absolutePath)
    }
}

tasks.register<JavaExec>("sealSecret") {
    group = "verification"
    description = "Seals a value with a repository public key using the app crypto implementation."
    dependsOn(jvmCompilation.compileTaskProvider)
    classpath = jvmCompilation.output.allOutputs + jvmCompilation.runtimeDependencyFiles
    mainClass.set("app.remotehub.tools.SealSecretKt")
}
