package com.swadsangam.restaurant.domain

/**
 * Local cart arithmetic used for display while offline (PRD FR-06):
 *   Subtotal = Σ(price × quantity)
 *   Total    = Subtotal + Taxes + Fees − Discounts
 * Before an order is accepted the server always recalculates with current prices.
 */
object CartMath {
    fun subtotal(lines: List<CartLine>): Int = lines.filter { it.isOrderable }.sumOf { it.lineTotalPaise }

    fun localQuote(lines: List<CartLine>, info: RestaurantInfo?, type: OrderType): Quote {
        val subtotal = subtotal(lines)
        val packaging = if (info != null && type != OrderType.DINE_IN) info.packagingFeePaise else 0
        val delivery = if (info != null && type == OrderType.DELIVERY) info.deliveryFeePaise else 0
        val tax = if (info != null && !info.pricesIncludeTax && info.taxRateBps > 0) {
            Math.round(subtotal.toDouble() * info.taxRateBps / 10000.0).toInt()
        } else 0
        return Quote(
            subtotalPaise = subtotal,
            discountPaise = 0,
            taxPaise = tax,
            taxLabel = info?.taxLabel ?: "GST",
            taxRateBps = info?.taxRateBps ?: 0,
            pricesIncludeTax = info?.pricesIncludeTax ?: true,
            packagingFeePaise = packaging,
            deliveryFeePaise = delivery,
            totalPaise = subtotal + tax + packaging + delivery,
            couponError = null,
            unavailableItemIds = lines.filterNot { it.isOrderable }.map { it.itemId },
            minOrderPaise = when {
                info == null -> 0
                type == OrderType.DELIVERY -> maxOf(info.minOrderPaise, info.deliveryMinOrderPaise)
                else -> info.minOrderPaise
            },
        )
    }

    /** Stable key so the same dish with the same options merges into one line. */
    fun lineKey(itemId: String, optionIds: Collection<String>): String =
        itemId + "|" + optionIds.sorted().joinToString(",")
}
