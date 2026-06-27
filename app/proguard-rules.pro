# Gson
-keepattributes Signature
-keep class com.lm.android.tv.player.data.model.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
