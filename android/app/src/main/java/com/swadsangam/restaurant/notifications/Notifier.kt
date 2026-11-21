package com.swadsangam.restaurant.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.swadsangam.restaurant.MainActivity
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.domain.OrderStatus

/** Customer order notifications (PRD FR-10). Push is supplementary; status is always in-app. */
object Notifier {
    const val CHANNEL_ID = "order_updates"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun titleFor(context: Context, status: OrderStatus): Int? = when (status) {
        OrderStatus.PLACED -> R.string.notif_submitted
        OrderStatus.ACCEPTED -> R.string.notif_accepted
        OrderStatus.PREPARING -> R.string.notif_preparing
        OrderStatus.READY -> R.string.notif_ready
        OrderStatus.OUT_FOR_DELIVERY -> R.string.notif_out_for_delivery
        OrderStatus.COMPLETED -> R.string.notif_completed
        OrderStatus.REJECTED -> R.string.notif_rejected
        OrderStatus.CANCELLED -> R.string.notif_cancelled
        OrderStatus.UNKNOWN -> null
    }

    fun showOrderStatus(context: Context, token: String?, orderNumber: String, status: OrderStatus, reason: String? = null) {
        val title = titleFor(context, status)?.let { context.getString(it) } ?: return
        var body = context.getString(R.string.notif_body, orderNumber)
        if (!reason.isNullOrBlank() && status in setOf(OrderStatus.REJECTED, OrderStatus.CANCELLED)) {
            body += " " + context.getString(R.string.reason_prefix, reason)
        }
        show(context, title, body, token, (token ?: orderNumber).hashCode())
    }

    fun show(context: Context, title: String, body: String, token: String?, id: Int) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = token?.let { Uri.parse("swadsangam://order/$it") }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_red))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // permission revoked — status remains visible in the app
        }
    }
}
