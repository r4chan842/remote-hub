import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
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
            implementation(libs.androidx.biometric)
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

android {
    namespace = "app.remotehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.remotehub"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
        jniLibs.useLegacyPackaging = false
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
        )
    }

    buildFeatures { buildConfig = true }
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
