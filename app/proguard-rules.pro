# Add project specific ProGuard rules here.

# Keep app entry points
-keep public class com.futureclock.app.FutureClockApp { *; }
-keep public class com.futureclock.app.MainActivity { *; }

# Keep widget providers
-keep public class * extends android.appwidget.AppWidgetProvider { *; }

# Keep service classes
-keep public class * extends android.app.Service { *; }

# Keep receivers
-keep public class * extends android.content.BroadcastReceiver { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep our data classes (used in widget intents)
-keep class com.futureclock.app.data.** { *; }
