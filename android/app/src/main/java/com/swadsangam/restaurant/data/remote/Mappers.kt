package com.swadsangam.restaurant.data.remote

import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.domain.OpeningSlot
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.domain.OrderLine
import com.swadsangam.restaurant.domain.OrderStatus
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import com.swadsangam.restaurant.domain.Quote
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.domain.RestaurantStatus
import com.swadsangam.restaurant.domain.StatusEvent

fun SettingsDto.toDomain() = RestaurantInfo(
    name = restaurantName, tagline = tagline, phone = phone, whatsappPhone = whatsappPhone,
    supportEmail = supportEmail, address = address, latitude = latitude, longitude = longitude,
    mapsQuery = mapsQuery,
    openingHours = openingHours.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.map { s -> OpeningSlot(s.open, s.close) } } }.toMap(),
    hoursVerified = hoursVerified, temporarilyClosed = temporarilyClosed, closureMessage = closureMessage,
    announcement = announcement, acceptingOrders = acceptingOrders, pickupEnabled = pickupEnabled,
    dineInEnabled = dineInEnabled, deliveryEnabled = deliveryEnabled, cashEnabled = cashEnabled,
    upiAtRestaurantEnabled = upiAtRestaurantEnabled, upiId = upiId, minOrderPaise = minOrderPaise,
    deliveryMinOrderPaise = deliveryMinOrderPaise, deliveryFeePaise = deliveryFeePaise,
    deliveryAreaNote = deliveryAreaNote, deliveryRadiusKm = deliveryRadiusKm,
    packagingFeePaise = packagingFeePaise, taxRateBps = taxRateBps, taxLabel = taxLabel,
    pricesIncludeTax = pricesIncludeTax, budgetThresholdPaise = budgetThresholdPaise,
    maxItemQuantity = maxItemQuantity, fssaiNumber = fssaiNumber, gstin = gstin,
    legalBusinessName = legalBusinessName, parkingInfo = parkingInfo, dineInInfo = dineInInfo, requireTableCode = requireTableCode,
    rating = rating, reviewCount = reviewCount, reviewsUrl = reviewsUrl,
    privacyPolicyUrl = privacyPolicyUrl, termsUrl = termsUrl,
)

fun StatusDto.toDomain() = RestaurantStatus(isOpenNow, acceptingOrders, temporarilyClosed, closureMessage, canOrder)

fun QuoteDto.toDomain() = Quote(
    subtotalPaise, discountPaise, taxPaise, taxLabel, taxRateBps, pricesIncludeTax,
    packagingFeePaise, deliveryFeePaise, totalPaise, couponError, unavailableItemIds, minOrderPaise,
)

fun foodTypeOf(s: String) = if (s == "NON_VEG") FoodType.NON_VEG else FoodType.VEG

fun OrderDto.toDomain(token: String) = Order(
    id = id,
    accessToken = accessToken ?: token,
    orderNumber = orderNumber,
    status = OrderStatus.parse(orderStatus),
    paymentStatus = paymentStatus,
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.CASH),
    orderType = runCatching { OrderType.valueOf(orderType) }.getOrDefault(OrderType.PICKUP),
    tableNumber = tableNumber,
    customerName = customerName,
    subtotalPaise = subtotalPaise,
    taxPaise = taxPaise,
    packagingFeePaise = packagingFeePaise,
    deliveryFeePaise = deliveryFeePaise,
    discountPaise = discountPaise,
    totalPaise = totalPaise,
    couponCode = couponCode,
    rejectionReason = rejectionReason,
    cancelReason = cancelReason,
    cancelledBy = cancelledBy,
    estimatedReadyAt = estimatedReadyAt,
    createdAt = createdAt,
    restaurantPhone = restaurantPhone,
    canCancel = canCancel,
    items = items.map {
        OrderLine(it.itemId, it.nameSnapshot, foodTypeOf(it.foodType), it.unitPricePaise, it.quantity,
            it.options.map { o -> o.name }, it.lineTotalPaise)
    },
    history = history.map { StatusEvent(OrderStatus.parse(it.status), it.at) },
    deliveryOtp = deliveryOtp?.takeIf { it.length == 4 && it.all(Char::isDigit) },
)
