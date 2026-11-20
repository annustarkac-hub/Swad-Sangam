package com.swadsangam.restaurant

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SwadSangamApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var orders: OrderRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        setupFirebase()
        analytics.track(Analytics.APP_OPENED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = analytics.flushAsync()
        })
    }

    /** Firebase is optional: only active when google-services.json was present at build time. */
    private fun setupFirebase() {
        if (!BuildConfig.FIREBASE_ENABLED || FirebaseApp.getApps(this).isEmpty()) return
        runCatching {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                appScope.launch { orders.updatePushToken(token) }
            }
        }
    }

    // Optimised image loading with memory + disk cache (PRD §13)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(50L * 1024 * 1024).build() }
            .build()
}
