package com.swadsangam.restaurant.data.remote

import com.swadsangam.restaurant.domain.ItemOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("name_hi") val nameHi: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class MenuItemDto(
    val id: String,
    val name: String,
    @SerialName("name_hi") val nameHi: String? = null,
    @SerialName("category_id") val categoryId: String,
    val description: String? = null,
    @SerialName("description_hi") val descriptionHi: String? = null,
    @SerialName("price_paise") val pricePaise: Int,
    @SerialName("food_type") val foodType: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_popular") val isPopular: Boolean = false,
    @SerialName("preparation_minutes") val preparationMinutes: Int? = null,
    val allergens: String? = null,
    @SerialName("portion_info") val portionInfo: String? = null,
    val options: List<ItemOption> = emptyList(),
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("variant_base_label") val variantBaseLabel: String? = null,
)

@Serializable
data class OfferDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class SlotDto(val open: String, val close: String)

@Serializable
data class SettingsDto(
    @SerialName("restaurant_name") val restaurantName: String = "Swad Sangam",
    val tagline: String = "Good Food. Honest Prices.",
    val phone: String = "+919675750485",
    @SerialName("whatsapp_phone") val whatsappPhone: String? = null,
    @SerialName("support_email") val supportEmail: String? = null,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("maps_query") val mapsQuery: String = "Swad Sangam, Bhaktiyana, Srinagar, Uttarakhand",
    @SerialName("opening_hours") val openingHours: Map<String, List<SlotDto>> = emptyMap(),
    @SerialName("hours_verified") val hoursVerified: Boolean = false,
    @SerialName("temporarily_closed") val temporarilyClosed: Boolean = false,
    @SerialName("closure_message") val closureMessage: String? = null,
    val announcement: String? = null,
    @SerialName("accepting_orders") val acceptingOrders: Boolean = false,
    @SerialName("pickup_enabled") val pickupEnabled: Boolean = false,
    @SerialName("dine_in_enabled") val dineInEnabled: Boolean = false,
    @SerialName("delivery_enabled") val deliveryEnabled: Boolean = false,
    @SerialName("cash_enabled") val cashEnabled: Boolean = true,
    @SerialName("upi_at_restaurant_enabled") val upiAtRestaurantEnabled: Boolean = false,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("min_order_paise") val minOrderPaise: Int = 0,
    @SerialName("delivery_min_order_paise") val deliveryMinOrderPaise: Int = 0,
    @SerialName("delivery_fee_paise") val deliveryFeePaise: Int = 0,
    @SerialName("delivery_area_note") val deliveryAreaNote: String? = null,
    @SerialName("delivery_radius_km") val deliveryRadiusKm: Double? = null,
    @SerialName("packaging_fee_paise") val packagingFeePaise: Int = 0,
    @SerialName("tax_rate_bps") val taxRateBps: Int = 0,
    @SerialName("tax_label") val taxLabel: String = "GST",
    @SerialName("prices_include_tax") val pricesIncludeTax: Boolean = true,
    @SerialName("budget_threshold_paise") val budgetThresholdPaise: Int = 10000,
    @SerialName("max_item_quantity") val maxItemQuantity: Int = 20,
    @SerialName("fssai_number") val fssaiNumber: String? = null,
    val gstin: String? = null,
    @SerialName("legal_business_name") val legalBusinessName: String? = null,
    @SerialName("parking_info") val parkingInfo: String? = null,
    @SerialName("dine_in_info") val dineInInfo: String? = null,
    @SerialName("require_table_code") val requireTableCode: Boolean = false,
    val rating: Double? = null,
    @SerialName("review_count") val reviewCount: Int? = null,
    @SerialName("reviews_url") val reviewsUrl: String? = null,
    @SerialName("privacy_policy_url") val privacyPolicyUrl: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null,
)

@Serializable
data class StatusDto(
    val isOpenNow: Boolean = false,
    val acceptingOrders: Boolean = false,
    val temporarilyClosed: Boolean = false,
    val closureMessage: String? = null,
    /** Server's single answer to "can a customer order now?" (null on an older backend) */
    val canOrder: Boolean? = null,
)

@Serializable
data class QuoteDto(
    val subtotalPaise: Int = 0,
    val discountPaise: Int = 0,
    val taxPaise: Int = 0,
    val taxLabel: String = "GST",
    val taxRateBps: Int = 0,
    val pricesIncludeTax: Boolean = true,
    val packagingFeePaise: Int = 0,
    val deliveryFeePaise: Int = 0,
    val totalPaise: Int = 0,
    val couponError: String? = null,
    val unavailableItemIds: List<String> = emptyList(),
    val minOrderPaise: Int = 0,
)

@Serializable
data class OrderOptionDto(val id: String? = null, val name: String, val pricePaise: Int = 0)

@Serializable
data class OrderLineDto(
    val itemId: String,
    val nameSnapshot: String,
    val foodType: String = "VEG",
    val unitPricePaise: Int,
    val quantity: Int,
    val options: List<OrderOptionDto> = emptyList(),
    val lineTotalPaise: Int,
)

@Serializable
data class HistoryDto(val status: String, val at: String)

@Serializable
data class OrderDto(
    val id: String,
    val accessToken: String? = null,
    val orderNumber: String,
    val orderStatus: String,
    val paymentStatus: String = "PENDING",
    val paymentMethod: String = "CASH",
    val orderType: String = "PICKUP",
    val tableNumber: String? = null,
    val customerName: String = "",
    val subtotalPaise: Int = 0,
    val taxPaise: Int = 0,
    val packagingFeePaise: Int = 0,
    val deliveryFeePaise: Int = 0,
    val discountPaise: Int = 0,
    val totalPaise: Int = 0,
    val couponCode: String? = null,
    val rejectionReason: String? = null,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val estimatedReadyAt: String? = null,
    val createdAt: String,
    val restaurantPhone: String = "",
    val canCancel: Boolean = false,
    /** 4-digit handover code — only sent to the customer, only while a delivery is on its way */
    val deliveryOtp: String? = null,
    val items: List<OrderLineDto> = emptyList(),
    val history: List<HistoryDto> = emptyList(),
)

/** Structured RPC response: {"ok":true,"order":{...}} or {"ok":false,"error":"CODE",...} */
@Serializable
data class RpcResult(
    val ok: Boolean,
    val duplicate: Boolean = false,
    val order: OrderDto? = null,
    val error: String? = null,
    val message: String? = null,
    val details: JsonElement? = null,
)

// ---- request bodies ----

@Serializable
data class OrderLineRequest(val itemId: String, val quantity: Int, val optionIds: List<String> = emptyList())

@Serializable
data class OrderRequest(
    val idempotencyKey: String,
    val customerName: String,
    val customerPhone: String,
    val orderType: String,
    val paymentMethod: String,
    val items: List<OrderLineRequest>,
    val termsAccepted: Boolean,
    val tableNumber: String? = null,
    val tableCode: String? = null,
    val deliveryAddress: String? = null,
    val deliveryLandmark: String? = null,
    val pickupInstructions: String? = null,
    val notes: String? = null,
    val couponCode: String? = null,
    val expectedTotalPaise: Int? = null,
    val fcmToken: String? = null,
    val sessionId: String? = null,
)

@Serializable
data class QuoteRequest(val orderType: String, val items: List<OrderLineRequest>, val couponCode: String? = null)

@Serializable data class PlaceOrderBody(@SerialName("p_order") val order: OrderRequest)
@Serializable data class QuoteBody(@SerialName("p_order") val order: QuoteRequest)
@Serializable data class TokenBody(@SerialName("p_token") val token: String)
@Serializable data class TokensBody(@SerialName("p_tokens") val tokens: List<String>)
@Serializable data class CancelBody(@SerialName("p_token") val token: String, @SerialName("p_reason") val reason: String)
@Serializable data class PushTokenBody(@SerialName("p_token") val token: String, @SerialName("p_fcm_token") val fcmToken: String)

@Serializable
data class FeedbackRequest(
    val orderToken: String? = null,
    val overall: Int,
    val foodQuality: Int? = null,
    val orderAccuracy: Int? = null,
    val packaging: Int? = null,
    val staffBehaviour: Int? = null,
    val comments: String? = null,
    val consentToPublish: Boolean = false,
)
@Serializable data class FeedbackBody(val p: FeedbackRequest)

@Serializable
data class EventDto(val name: String, val sessionId: String, val at: String, val properties: Map<String, String> = emptyMap())
@Serializable data class EventsBody(@SerialName("p_events") val events: List<EventDto>)

// ---------- Customer accounts (Supabase Auth email + password, free) ----------

@Serializable
data class AuthUserDto(val id: String, val email: String? = null)

/** Returned by sign-in, refresh and (when email confirmation is off) sign-up. */
@Serializable
data class SessionDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthUserDto? = null,
    // sign-up without a session returns the user fields at the top level
    val id: String? = null,
)

@Serializable data class PasswordLogin(val email: String, val password: String)
@Serializable data class SignUpBody(val email: String, val password: String, val data: Map<String, String>)
@Serializable data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)
@Serializable data class RecoverBody(val email: String)

@Serializable
data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val phone: String,
)

@Serializable
data class AddressDto(
    val id: Long,
    val label: String,
    val address: String,
    val landmark: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class AddressWrite(
    val label: String,
    val address: String,
    val landmark: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable data class MyOrdersBody(@SerialName("p_limit") val limit: Int = 100)
