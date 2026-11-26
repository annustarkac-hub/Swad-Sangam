package com.swadsangam.restaurant.data.repo

import kotlinx.coroutines.sync.withLock
import com.swadsangam.restaurant.data.local.MyOrderEntity
import com.swadsangam.restaurant.data.local.OrderDao
import com.swadsangam.restaurant.data.prefs.UserPrefs
import com.swadsangam.restaurant.data.remote.CancelBody
import com.swadsangam.restaurant.data.remote.FeedbackBody
import com.swadsangam.restaurant.data.remote.FeedbackRequest
import com.swadsangam.restaurant.data.remote.OrderDto
import com.swadsangam.restaurant.data.remote.OrderLineRequest
import com.swadsangam.restaurant.data.remote.OrderRequest
import com.swadsangam.restaurant.data.remote.PlaceOrderBody
import com.swadsangam.restaurant.data.remote.PushTokenBody
import com.swadsangam.restaurant.data.remote.QuoteBody
import com.swadsangam.restaurant.data.remote.QuoteRequest
import com.swadsangam.restaurant.data.remote.SupabaseApi
import com.swadsangam.restaurant.data.remote.TokenBody
import com.swadsangam.restaurant.data.remote.TokensBody
import com.swadsangam.restaurant.data.remote.toDomain
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.domain.OrderStatus
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import com.swadsangam.restaurant.domain.Quote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CheckoutForm(
    val name: String,
    val phone: String,
    val orderType: OrderType,
    val paymentMethod: PaymentMethod,
    val tableNumber: String?,
    val tableCode: String? = null,
    val deliveryAddress: String?,
    val deliveryLandmark: String?,
    val pickupInstructions: String?,
    val notes: String?,
    val couponCode: String?,
    val termsAccepted: Boolean,
    /** Delivery pin; the server computes distance and fee from it. */
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,
)

/** Everything that makes two submissions "the same order" (decides whether a retry may reuse the idempotency key). */
internal fun orderFingerprint(form: CheckoutForm, lines: List<CartLine>): String =
    listOf(
        form.orderType.name, form.paymentMethod.name, form.couponCode.orEmpty().trim().uppercase(),
        form.tableNumber.orEmpty().trim(), form.deliveryAddress.orEmpty().trim(), "${form.deliveryLat},${form.deliveryLng}",
        lines.map { "${it.itemId}:${it.optionIds.sorted().joinToString("+")}x${it.quantity}" }.sorted().joinToString(","),
    ).joinToString("|").hashCode().toString(16)

sealed interface PlaceResult {
    data class Success(val order: Order) : PlaceResult
    /** Business rule rejection from the server (e.g. PRICE_CHANGED, ITEM_UNAVAILABLE, ORDERS_CLOSED). */
    data class Rejected(val code: String, val message: String, val unavailableItemIds: List<String> = emptyList()) : PlaceResult
    /** No connection / timeout. Retrying is safe: the same idempotency key is reused. */
    data object NetworkError : PlaceResult
    data class Failed(val message: String) : PlaceResult
}

@Singleton
class OrderRepository @Inject constructor(
    private val api: SupabaseApi,
    private val dao: OrderDao,
    private val prefs: UserPrefs,
    private val json: Json,
    private val analytics: Analytics,
) {
    val myOrders: Flow<List<Order>> = dao.all().map { list -> list.mapNotNull { it.toOrder() } }

    fun observe(token: String): Flow<Order?> = dao.byToken(token).map { it?.toOrder() }

    private fun MyOrderEntity.toOrder(): Order? =
        runCatching { json.decodeFromString(OrderDto.serializer(), lastJson).toDomain(accessToken) }.getOrNull()

    private fun lineRequests(lines: List<CartLine>) =
        lines.map { OrderLineRequest(it.itemId, it.quantity, it.optionIds) }

    suspend fun quote(lines: List<CartLine>, type: OrderType, coupon: String?, lat: Double? = null, lng: Double? = null): Quote? = try {
        val pin = type == OrderType.DELIVERY && lat != null && lng != null
        api.quote(QuoteBody(QuoteRequest(type.name, lineRequests(lines), coupon?.takeIf { it.isNotBlank() },
            deliveryLat = lat.takeIf { pin }, deliveryLng = lng.takeIf { pin }))).toDomain()
    } catch (e: Exception) {
        null
    }

    suspend fun place(form: CheckoutForm, lines: List<CartLine>, expectedTotalPaise: Int?): PlaceResult {
        val key = prefs.idempotencyKeyFor(orderFingerprint(form, lines))
        val request = OrderRequest(
            idempotencyKey = key,
            customerName = form.name.trim(),
            customerPhone = form.phone,
            orderType = form.orderType.name,
            paymentMethod = form.paymentMethod.name,
            items = lineRequests(lines),
            termsAccepted = form.termsAccepted,
            tableNumber = form.tableNumber?.takeIf { it.isNotBlank() && form.orderType == OrderType.DINE_IN },
            tableCode = form.tableCode?.takeIf { it.isNotBlank() && form.orderType == OrderType.DINE_IN },
            deliveryAddress = form.deliveryAddress?.takeIf { form.orderType == OrderType.DELIVERY },
            deliveryLandmark = form.deliveryLandmark?.takeIf { form.orderType == OrderType.DELIVERY },
            deliveryLat = form.deliveryLat?.takeIf { form.orderType == OrderType.DELIVERY && form.deliveryLng != null },
            deliveryLng = form.deliveryLng?.takeIf { form.orderType == OrderType.DELIVERY && form.deliveryLat != null },
            pickupInstructions = form.pickupInstructions,
            notes = form.notes,
            couponCode = form.couponCode?.takeIf { it.isNotBlank() },
            expectedTotalPaise = expectedTotalPaise,
            fcmToken = prefs.fcmToken(),
            sessionId = analytics.sessionId,
        )
        return try {
            val res = api.placeOrder(PlaceOrderBody(request))
            if (res.ok && res.order != null) {
                prefs.clearIdempotencyKey()
                prefs.saveCustomer(form.name.trim(), form.phone)
                val token = res.order.accessToken ?: return PlaceResult.Failed("Missing order token")
                save(res.order, token, notified = res.order.orderStatus)
                PlaceResult.Success(res.order.toDomain(token))
            } else {
                // Business rejection: a fresh key is fine for the next attempt.
                prefs.clearIdempotencyKey()
                val unavailable = runCatching {
                    (res.details as? JsonObject)?.get("items")?.jsonArray?.map { it.jsonObject["itemId"]!!.jsonPrimitive.content }
                }.getOrNull().orEmpty()
                PlaceResult.Rejected(res.error ?: "UNKNOWN", res.message ?: "Order could not be placed.", unavailable)
            }
        } catch (e: IOException) {
            PlaceResult.NetworkError
        } catch (e: Exception) {
            PlaceResult.Failed(e.message ?: "Unexpected error")
        }
    }

    /** Adds an order from the customer's account (placed on another phone) to this phone's list. */
    suspend fun saveFromAccount(dto: OrderDto) {
        val token = dto.accessToken ?: return
        save(dto, token, notified = if (dao.byTokenNow(token) == null) dto.orderStatus else null)
    }

    private suspend fun save(dto: OrderDto, token: String, notified: String? = null) {
        val existing = dao.byTokenNow(token)
        dao.upsert(
            MyOrderEntity(
                accessToken = token,
                orderId = dto.id,
                orderNumber = dto.orderNumber,
                status = dto.orderStatus,
                totalPaise = dto.totalPaise,
                createdAt = dto.createdAt,
                itemsSummary = dto.items.joinToString { "${it.quantity}× ${it.nameSnapshot}" },
                lastJson = json.encodeToString(OrderDto.serializer(), dto.copy(accessToken = token)),
                notifiedStatus = notified ?: existing?.notifiedStatus ?: dto.orderStatus,
                feedbackGiven = existing?.feedbackGiven ?: false,
            ),
        )
    }

    /** Refreshes one order from the server. Returns null when offline. */
    suspend fun refresh(token: String): Order? = try {
        val el = api.order(TokenBody(token))
        if (el is JsonNull) null else {
            val dto = json.decodeFromJsonElement(OrderDto.serializer(), el)
            save(dto, token)
            dto.toDomain(token)
        }
    } catch (e: Exception) {
        null
    }

    data class StatusChange(val token: String, val orderNumber: String, val status: OrderStatus, val reason: String?,
                            val type: com.swadsangam.restaurant.domain.OrderType? = null)

    /**
     * Refreshes all orders on this device and returns status changes that have not
     * been notified yet (used by the background worker when push is unavailable).
     */
    /**
     * Notification state per order = the last status the customer was told about (notifiedStatus).
     * Three senders (in-app check, background job, push) may run at the same moment; this lock makes
     * "compare + mark as notified" one step, so each change is announced exactly once.
     */
    private val notifyLock = kotlinx.coroutines.sync.Mutex()

    /** For push: true only for the first caller that announces this status for this order. */
    suspend fun claimNotification(token: String, status: OrderStatus): Boolean = notifyLock.withLock {
        val e = dao.byTokenNow(token) ?: return@withLock true
        if (e.notifiedStatus == status.name) false else { dao.upsert(e.copy(notifiedStatus = status.name)); true }
    }

    suspend fun refreshAllAndCollectChanges(): List<StatusChange> = notifyLock.withLock { collectChangesLocked() }

    private suspend fun collectChangesLocked(): List<StatusChange> {
        val local = dao.allNow()
        if (local.isEmpty()) return emptyList()
        val remote = try {
            api.orders(TokensBody(local.take(50).map { it.accessToken }))
        } catch (e: Exception) {
            return emptyList()
        }
        val changes = mutableListOf<StatusChange>()
        for (dto in remote) {
            val token = dto.accessToken ?: continue
            val old = local.firstOrNull { it.accessToken == token }
            if (old != null && old.notifiedStatus != dto.orderStatus) {
                changes += StatusChange(token, dto.orderNumber, OrderStatus.parse(dto.orderStatus), dto.rejectionReason ?: dto.cancelReason,
                    runCatching { com.swadsangam.restaurant.domain.OrderType.valueOf(dto.orderType) }.getOrNull())
            }
            save(dto, token, notified = dto.orderStatus)
        }
        return changes
    }

    suspend fun markNotified(token: String, status: OrderStatus) {
        dao.byTokenNow(token)?.let { dao.upsert(it.copy(notifiedStatus = status.name)) }
    }

    suspend fun myOrdersTokenFor(orderNumber: String): String? =
        dao.allNow().firstOrNull { it.orderNumber == orderNumber }?.accessToken

    suspend fun hasActiveOrders(): Boolean = dao.allNow().any { OrderStatus.parse(it.status).isActive }

    suspend fun cancel(token: String, reason: String): Result<Order> = try {
        val res = api.cancel(CancelBody(token, reason))
        if (res.ok && res.order != null) {
            save(res.order, token, notified = res.order.orderStatus)
            Result.success(res.order.toDomain(token))
        } else Result.failure(IllegalStateException(res.message ?: "Could not cancel"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updatePushToken(fcm: String) {
        prefs.setFcmToken(fcm)
        dao.allNow().filter { OrderStatus.parse(it.status).isActive }.forEach {
            runCatching { api.setPushToken(PushTokenBody(it.accessToken, fcm)) }
        }
    }

    suspend fun submitFeedback(req: FeedbackRequest): Result<Unit> = try {
        val res = api.submitFeedback(FeedbackBody(req))
        if (res.ok) {
            req.orderToken?.let { t -> dao.byTokenNow(t)?.let { dao.upsert(it.copy(feedbackGiven = true)) } }
            Result.success(Unit)
        } else Result.failure(IllegalStateException(res.message ?: "Could not send feedback"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun feedbackGiven(token: String) = dao.byTokenNow(token)?.feedbackGiven ?: false

    /** Live "already rated" flag, so the Rate button disappears right after the feedback screen returns. */
    fun observeFeedbackGiven(token: String): Flow<Boolean> = dao.byToken(token).map { it?.feedbackGiven ?: false }

    suspend fun clearHistory() = dao.clear()
}
