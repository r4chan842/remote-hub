import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
