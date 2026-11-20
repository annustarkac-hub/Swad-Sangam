package com.swadsangam.restaurant.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.domain.OrderStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives FCM pushes sent by the `push-order-status` Edge Function. */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var orders: OrderRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { orders.updatePushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val number = message.data["orderNumber"] ?: return
        val status = OrderStatus.parse(message.data["status"])
        scope.launch {
            // Find our token for this order, refresh it so the in-app state matches.
            val token = orders.myOrdersTokenFor(number)
            if (token != null) {
                orders.refresh(token)
                orders.markNotified(token, status)
            }
            Notifier.showOrderStatus(applicationContext, token, number, status, null)
        }
    }
}
