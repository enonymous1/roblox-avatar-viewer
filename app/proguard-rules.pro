# Keep the JavaScript bridge: R8 must not strip or rename its
# @JavascriptInterface members, or the WebView scene cannot call them.
-keepclassmembers class com.enonymous1.robloxavatar.ui.AvatarBridge {
    @android.webkit.JavascriptInterface <methods>;
}
# kotlinx.serialization: keep generated serializers referenced by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
