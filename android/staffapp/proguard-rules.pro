# Keep the JavaScript bridge methods (called by name from the web app)
-keepclassmembers class in.swadsangam.staff.MainActivity$AppBridge { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface
