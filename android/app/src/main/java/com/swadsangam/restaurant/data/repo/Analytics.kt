package com.swadsangam.restaurant.data.repo

import com.swadsangam.restaurant.BuildConfig
import com.swadsangam.restaurant.data.remote.EventDto
import com.swadsangam.restaurant.data.remote.EventsBody
import com.swadsangam.restaurant.data.remote.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-conscious analytics (PRD §14): a random id per app session (not per device),
 * no advertising id, no personal data. Events are batched and sent to our own database.
 */
@Singleton
class Analytics @Inject constructor(private val api: SupabaseApi) {
    val sessionId: String = UUID.randomUUID().toString()
    private val buffer = mutableListOf<EventDto>()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun track(name: String, vararg props: Pair<String, Any?>) {
        val event = EventDto(name, sessionId, Instant.now().toString(),
            props.filter { it.second != null }.associate { it.first to it.second.toString() })
        scope.launch {
            val flushNow = lock.withLock { buffer += event; buffer.size >= 20 }
            if (flushNow) flush()
        }
    }

    fun flushAsync() { scope.launch { flush() } }

    private suspend fun flush() {
        if (BuildConfig.SUPABASE_URL.isBlank()) return
        val batch = lock.withLock { buffer.toList().also { buffer.clear() } }
        if (batch.isEmpty()) return
        runCatching { api.logEvents(EventsBody(batch)) }
            .onFailure { lock.withLock { if (buffer.size < 200) buffer.addAll(0, batch) } }
    }

    companion object {
        const val APP_OPENED = "app_opened"
        const val HOME_VIEWED = "home_viewed"
        const val MENU_OPENED = "menu_opened"
        const val ITEM_VIEWED = "item_viewed"
        const val ITEM_ADDED = "item_added_to_cart"
        const val CART_VIEWED = "cart_viewed"
        const val CHECKOUT_STARTED = "checkout_started"
        const val ORDER_CANCELLED = "order_cancelled"
        const val DIRECTIONS = "directions_clicked"
        const val CALL = "call_clicked"
        const val SEARCH = "search_performed"
        const val SHARE = "share_clicked"
    }
}
