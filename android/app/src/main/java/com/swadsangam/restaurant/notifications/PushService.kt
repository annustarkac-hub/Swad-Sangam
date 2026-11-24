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
        // Data-only message from push-order-status: runs in foreground AND background, so we always
        // refresh the order and show our own localized notification that opens the order.
        val number = message.data["orderNumber"] ?: return
        val status = OrderStatus.parse(message.data["status"])
        val type = runCatching { com.swadsangam.restaurant.domain.OrderType.valueOf(message.data["orderType"] ?: "") }.getOrNull()
        val reason = message.data["reason"]?.takeIf { it.isNotBlank() }
        scope.launch {
            val token = message.data["token"]?.takeIf { it.isNotBlank() } ?: orders.myOrdersTokenFor(number)
            if (token != null) {
                orders.refresh(token)
                orders.markNotified(token, status)
            }
            Notifier.showOrderStatus(applicationContext, token, number, status, reason, type)
        }
    }
}
