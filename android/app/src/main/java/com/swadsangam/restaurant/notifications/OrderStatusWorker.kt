package com.swadsangam.restaurant.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
 * Fallback status checker that works without Firebase: while the customer has an
 * active order, check every 15 minutes (Android's minimum) and notify on changes.
 * Stops itself when no orders are active.
 */
class OrderStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun orders(): OrderRepository }

    override suspend fun doWork(): Result {
        val orders = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).orders()
        orders.refreshAllAndCollectChanges().forEach {
            Notifier.showOrderStatus(applicationContext, it.token, it.orderNumber, it.status, it.reason)
        }
        if (!orders.hasActiveOrders()) cancel(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "order-status-check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<OrderStatusWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
