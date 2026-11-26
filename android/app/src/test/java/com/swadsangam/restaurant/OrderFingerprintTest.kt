package com.swadsangam.restaurant

import com.swadsangam.restaurant.data.repo.CheckoutForm
import com.swadsangam.restaurant.data.repo.orderFingerprint
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A retry may reuse the idempotency key only for exactly the same order. */
class OrderFingerprintTest {
    private val form = CheckoutForm("Asha", "9876500001", OrderType.PICKUP, PaymentMethod.CASH, null, null, null, null, null, null, null, true)
    private fun line(id: String, q: Int, opts: List<String> = emptyList()) = CartLine("$id|${opts.joinToString(",")}", null, id, opts, q)

    @Test fun sameOrderSameFingerprint_evenIfLineOrderDiffers() {
        assertEquals(orderFingerprint(form, listOf(line("a", 1), line("b", 2))), orderFingerprint(form, listOf(line("b", 2), line("a", 1))))
    }

    @Test fun changedQuantityGivesNewFingerprint() {
        assertNotEquals(orderFingerprint(form, listOf(line("a", 1))), orderFingerprint(form, listOf(line("a", 2))))
    }

    @Test fun changedOrderTypeOrCouponGivesNewFingerprint() {
        val l = listOf(line("a", 1))
        assertNotEquals(orderFingerprint(form, l), orderFingerprint(form.copy(orderType = OrderType.DINE_IN, tableNumber = "T1"), l))
        assertNotEquals(orderFingerprint(form, l), orderFingerprint(form.copy(couponCode = "STUDENT10"), l))
    }

    @Test fun movedDeliveryPinGivesNewFingerprint() {
        val l = listOf(line("a", 1))
        val d = form.copy(orderType = OrderType.DELIVERY, deliveryAddress = "Hostel 3, NIT Srinagar")
        assertNotEquals(orderFingerprint(d.copy(deliveryLat = 30.25, deliveryLng = 78.78), l), orderFingerprint(d.copy(deliveryLat = 30.32, deliveryLng = 78.78), l))
    }

    @Test fun nameChangeDoesNotMatter() {
        val l = listOf(line("a", 1))
        assertEquals(orderFingerprint(form, l), orderFingerprint(form.copy(name = "Asha Rawat"), l))
    }
}
