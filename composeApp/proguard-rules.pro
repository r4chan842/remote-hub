# Shrinking and optimisation stay on; obfuscation is intentionally disabled so that
# crash reports from the field remain readable without a mapping file.
-dontobfuscate

-keep class app.remotehub.tsbridge.** { *; }
-keep class app.remotehub.rdp.NativeRdp { *; }
-keep interface app.remotehub.rdp.RdpListener { *; }
-keepclassmembers class * implements app.remotehub.rdp.RdpListener {
    void onState(int, java.lang.String);
    void onResize(int, int);
    void onClipboard(byte[]);
}
-keep class app.remotehub.MainActivity { *; }
-keep class app.remotehub.RemoteHubApplication { *; }
-keep class app.remotehub.net.SessionService { *; }
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-keep class kotlinx.serialization.json.** { *; }
-keepclassmembers class app.remotehub.github.** { *; }
-keepclassmembers class app.remotehub.net.NodeStatus { *; }
-keepclassmembers class app.remotehub.net.TailnetPeer { *; }
-keepclassmembers class app.remotehub.net.** { *; }
-keep,includedescriptorclasses class io.ktor.** { *; }
