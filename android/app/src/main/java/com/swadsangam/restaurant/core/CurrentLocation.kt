package com.swadsangam.restaurant.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** One-shot device location for the delivery pin, using the platform LocationManager (no Google Play services needed).
    Returns null when location is off, not permitted, or no fix arrives within [timeoutMs]. */
object CurrentLocation {
    @SuppressLint("MissingPermission")  // callers request ACCESS_FINE/COARSE_LOCATION first; SecurityException is caught
    suspend fun get(context: Context, timeoutMs: Long = 20_000): Location? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
        if (providers.isEmpty()) null else {
            // a recent fix (< 2 min) is good enough and instant
            val recent = providers.mapNotNull { lm.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 120_000 }.minByOrNull { it.accuracy }
            recent ?: withTimeoutOrNull(timeoutMs) { fresh(lm, providers.first()) }
        }
    } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = android.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, Executors.newSingleThreadExecutor()) { loc -> if (cont.isActive) cont.resume(loc) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) { lm.removeUpdates(this); if (cont.isActive) cont.resume(location) }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }
    }
}
