package com.swadsangam.restaurant.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.swadsangam.restaurant.data.repo.OrderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Status checker that works WITHOUT Firebase (free): while the customer has an active order it checks
 * about once a minute (a self-renewing one-time job; Android may stretch this in battery saver) and shows
 * a top-bar notification for every change. A 15-minute periodic job is the safety net. Both stop when no
 * order is active. While the app is open, AppViewModel checks every 20 s instead.
 */
class OrderStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun orders(): OrderRepository }

    override suspend fun doWork(): Result {
        val orders = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).orders()
        orders.refreshAllAndCollectChanges().forEach {
            Notifier.showOrderStatus(applicationContext, it.token, it.orderNumber, it.status, it.reason, it.type)
        }
        if (orders.hasActiveOrders()) {
            if (tags.contains(FAST)) scheduleFast(applicationContext)
        } else cancel(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "order-status-check"
        private const val FAST = "order-status-fast"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<OrderStatusWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            scheduleFast(context)
        }

        private fun scheduleFast(context: Context) {
            val req = OneTimeWorkRequestBuilder<OrderStatusWorker>().setInitialDelay(60, TimeUnit.SECONDS)
                .setConstraints(network).addTag(FAST).build()
            WorkManager.getInstance(context).enqueueUniqueWork(FAST, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
            WorkManager.getInstance(context).cancelUniqueWork(FAST)
        }
    }
}
