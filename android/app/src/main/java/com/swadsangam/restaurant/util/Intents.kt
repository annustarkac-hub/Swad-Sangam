package com.swadsangam.restaurant.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.domain.RestaurantInfo

/** External actions — no special permissions required (dialer, not direct call). */
object Intents {
    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    fun call(context: Context, phone: String) = safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    fun mapsUrl(info: RestaurantInfo): String {
        val dest = if (info.latitude != null && info.longitude != null) "${info.latitude},${info.longitude}"
        else info.mapsQuery
        return "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(dest)
    }

    /** Opens Google Maps (app if installed, otherwise browser) with directions to the restaurant. */
    fun directions(context: Context, info: RestaurantInfo) =
        safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl(info))))

    fun share(context: Context, info: RestaurantInfo) {
        val text = context.getString(
            R.string.share_text, info.name, info.address, info.phone, mapsUrl(info),
            "https://github.com/annustarkac-hub/Swad-Sangam/releases/latest/download/swad-sangam.apk",
        )
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        safeStart(context, Intent.createChooser(send, context.getString(R.string.share)))
    }

    fun openUrl(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun email(context: Context, address: String) =
        safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))

    /**
     * Opens the customer's UPI app to pay at the restaurant. The app NEVER treats this
     * as proof of payment — staff confirm receipt in the dashboard (PRD FR-08).
     */
    fun upiPay(context: Context, upiId: String, payee: String, amountPaise: Int, orderNumber: String) {
        val uri = Uri.Builder().scheme("upi").authority("pay")
            .appendQueryParameter("pa", upiId)
            .appendQueryParameter("pn", payee)
            .appendQueryParameter("am", String.format(java.util.Locale.US, "%.2f", amountPaise / 100.0))
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Order $orderNumber")
            .build()
        safeStart(context, Intent(Intent.ACTION_VIEW, uri))
    }
}
