package com.swadsangam.restaurant.domain

import kotlinx.serialization.Serializable

enum class FoodType { VEG, NON_VEG }

enum class OrderType { PICKUP, DINE_IN, DELIVERY }

enum class PaymentMethod { CASH, UPI_AT_RESTAURANT, ONLINE }

enum class OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY, OUT_FOR_DELIVERY, COMPLETED, REJECTED, CANCELLED, UNKNOWN;

    val isActive get() = this in setOf(PLACED, ACCEPTED, PREPARING, READY, OUT_FOR_DELIVERY)

    companion object {
        fun parse(s: String?): OrderStatus = entries.firstOrNull { it.name == s } ?: UNKNOWN
    }
}

@Serializable
data class ItemOption(
    val id: String,
    val name: String,
    val pricePaise: Int = 0,
    val isAvailable: Boolean = true,
    /** Options in the same group are alternatives (e.g. "size": Half / Full) — pick one at most. */
    val group: String? = null,
)

data class Category(val id: String, val name: String, val nameHi: String?, val sortOrder: Int)

data class MenuItem(
    val id: String,
    val name: String,
    val nameHi: String?,
    val categoryId: String,
    val description: String?,
    val descriptionHi: String?,
    val pricePaise: Int,
    val foodType: FoodType,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val isPopular: Boolean,
    val preparationMinutes: Int?,
    val allergens: String?,
    val portionInfo: String?,
    val options: List<ItemOption>,
    val sortOrder: Int,
    /** Name of the base price when the dish has alternatives, e.g. "Half" (Full is an option). */
    val variantBaseLabel: String? = null,
) {
    /** Options grouped as alternatives (size, Dry/Gravy …), in menu order. */
    val variantGroups: Map<String, List<ItemOption>> get() = options.filter { !it.group.isNullOrBlank() }.groupBy { it.group!! }
    val addOns: List<ItemOption> get() = options.filter { it.group.isNullOrBlank() }

    fun displayName(hindi: Boolean) = if (hindi && !nameHi.isNullOrBlank()) nameHi else name
    fun displayDescription(hindi: Boolean) = if (hindi && !descriptionHi.isNullOrBlank()) descriptionHi else description
}

data class Offer(
    val id: Long,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val couponCode: String?,
)

/** A cart line resolved against the current menu. */
data class CartLine(
    val key: String,
    val item: MenuItem?,          // null when the dish was removed from the menu
    val itemId: String,
    val optionIds: List<String>,
    val quantity: Int,
) {
    val selectedOptions: List<ItemOption>
        get() = item?.options?.filter { it.id in optionIds }.orEmpty()
    val unitPricePaise: Int
        get() = (item?.pricePaise ?: 0) + selectedOptions.sumOf { it.pricePaise }
    val lineTotalPaise: Int get() = unitPricePaise * quantity
    val isOrderable: Boolean
        get() = item != null && item.isAvailable && selectedOptions.size == optionIds.size &&
            selectedOptions.all { it.isAvailable }
}

data class OpeningSlot(val open: String, val close: String)

data class RestaurantInfo(
    val name: String,
    val tagline: String,
    val phone: String,
    val whatsappPhone: String?,
    val supportEmail: String?,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val mapsQuery: String,
    val openingHours: Map<Int, List<OpeningSlot>>,
    val hoursVerified: Boolean,
    val temporarilyClosed: Boolean,
    val closureMessage: String?,
    val announcement: String?,
    val acceptingOrders: Boolean,
    val pickupEnabled: Boolean,
    val dineInEnabled: Boolean,
    val deliveryEnabled: Boolean,
    val cashEnabled: Boolean,
    val upiAtRestaurantEnabled: Boolean,
    val upiId: String?,
    val minOrderPaise: Int,
    val deliveryMinOrderPaise: Int,
    val deliveryFeePaise: Int,
    val deliveryAreaNote: String?,
    val deliveryRadiusKm: Double?,
    val packagingFeePaise: Int,
    val taxRateBps: Int,
    val taxLabel: String,
    val pricesIncludeTax: Boolean,
    val budgetThresholdPaise: Int,
    val maxItemQuantity: Int,
    val fssaiNumber: String?,
    val gstin: String?,
    val legalBusinessName: String?,
    val parkingInfo: String?,
    val dineInInfo: String?,
    val requireTableCode: Boolean = false,
    val rating: Double?,
    val reviewCount: Int?,
    val reviewsUrl: String?,
    val privacyPolicyUrl: String?,
    val termsUrl: String?,
) {
    val anyOrderTypeEnabled get() = pickupEnabled || dineInEnabled || deliveryEnabled
    val enabledOrderTypes: List<OrderType>
        get() = buildList {
            if (pickupEnabled) add(OrderType.PICKUP)
            if (dineInEnabled) add(OrderType.DINE_IN)
            if (deliveryEnabled) add(OrderType.DELIVERY)
        }
    val enabledPaymentMethods: List<PaymentMethod>
        get() = buildList {
            if (cashEnabled) add(PaymentMethod.CASH)
            if (upiAtRestaurantEnabled) add(PaymentMethod.UPI_AT_RESTAURANT)
        }
}

data class RestaurantStatus(
    val isOpenNow: Boolean,
    val acceptingOrders: Boolean,
    val temporarilyClosed: Boolean,
    val closureMessage: String?,
)

/** Server-calculated price breakdown (the server is the source of truth). */
data class Quote(
    val subtotalPaise: Int,
    val discountPaise: Int,
    val taxPaise: Int,
    val taxLabel: String,
    val taxRateBps: Int,
    val pricesIncludeTax: Boolean,
    val packagingFeePaise: Int,
    val deliveryFeePaise: Int,
    val totalPaise: Int,
    val couponError: String?,
    val unavailableItemIds: List<String>,
    val minOrderPaise: Int,
)

data class OrderLine(
    val itemId: String,
    val name: String,
    val foodType: FoodType,
    val unitPricePaise: Int,
    val quantity: Int,
    val options: List<String>,
    val lineTotalPaise: Int,
)

data class StatusEvent(val status: OrderStatus, val at: String)

data class Order(
    val id: String,
    val accessToken: String,
    val orderNumber: String,
    val status: OrderStatus,
    val paymentStatus: String,
    val paymentMethod: PaymentMethod,
    val orderType: OrderType,
    val tableNumber: String?,
    val customerName: String,
    val subtotalPaise: Int,
    val taxPaise: Int,
    val packagingFeePaise: Int,
    val deliveryFeePaise: Int,
    val discountPaise: Int,
    val totalPaise: Int,
    val couponCode: String?,
    val rejectionReason: String?,
    val cancelReason: String?,
    val cancelledBy: String?,
    val estimatedReadyAt: String?,
    val createdAt: String,
    val restaurantPhone: String,
    val canCancel: Boolean,
    val items: List<OrderLine>,
    val history: List<StatusEvent>,
)
