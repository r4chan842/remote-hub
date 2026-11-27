// Every plugin is declared here with `apply false` so that all of them are loaded
// by the same class loader. When the Android plugin is resolved only in a
// subproject, the Kotlin plugin (already on the root class path) cannot see
// AndroidGradlePluginVersion and fails with
// "Can't infer current AndroidGradlePluginVersion: Is the Android plugin applied?".
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
