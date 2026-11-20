package com.swadsangam.restaurant

import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.core.PhoneValidator
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.CartMath
import com.swadsangam.restaurant.domain.Category
import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.domain.ItemOption
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.domain.OrderStatus
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.data.remote.SettingsDto
import com.swadsangam.restaurant.data.remote.toDomain
import com.swadsangam.restaurant.ui.menu.FoodFilter
import com.swadsangam.restaurant.ui.menu.MenuFilter
import com.swadsangam.restaurant.ui.menu.MenuFilters
import com.swadsangam.restaurant.ui.menu.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessRulesTest {
    private fun item(id: String, price: Int, type: FoodType = FoodType.VEG, cat: String = "thalis", available: Boolean = true,
                     options: List<ItemOption> = emptyList()) =
        MenuItem(id, "Dish $id", null, cat, null, null, price, type, null, available, false, null, null, null, options, 0)

    private val menu = listOf(
        item("dish_001", 5000, cat = "breakfast").copy(name = "Mooli Paratha"),
        item("dish_004", 6000).copy(name = "Veg Rice with Roti Thali"),
        item("dish_007", 25000, FoodType.NON_VEG).copy(name = "Chicken Thali"),
        item("dish_011", 32000, FoodType.NON_VEG, cat = "chicken").copy(name = "Chicken Handi"),
    )
    private val cats = listOf(Category("breakfast", "Breakfast", null, 1), Category("thalis", "Thalis", null, 3), Category("chicken", "Chicken", null, 6))

    @Test fun money_formats_integer_paise() {
        assertEquals("₹50", Money.format(5000))
        assertEquals("₹1,250", Money.format(125000))
        assertEquals("₹12.50", Money.format(1250))
    }

    @Test fun phone_validation_accepts_indian_mobiles_only() {
        assertEquals("9876543210", PhoneValidator.normalize("+91 98765 43210"))
        assertEquals("9876543210", PhoneValidator.normalize("09876543210"))
        assertNull(PhoneValidator.normalize("12345"))
        assertNull(PhoneValidator.normalize("5876543210"))
    }

    @Test fun subtotal_is_sum_of_price_times_quantity() {
        val lines = listOf(
            CartLine("a", menu[0], "dish_001", emptyList(), 2),
            CartLine("b", menu[2], "dish_007", emptyList(), 1),
        )
        assertEquals(2 * 5000 + 25000, CartMath.subtotal(lines))
    }

    @Test fun options_add_to_unit_price_and_unavailable_lines_excluded() {
        val combo = item("dish_012", 30000, options = listOf(ItemOption("extra", "Extra", 2000)))
        val lines = listOf(
            CartLine("a", combo, "dish_012", listOf("extra"), 2),
            CartLine("b", menu[0].copy(isAvailable = false), "dish_001", emptyList(), 3),
            CartLine("c", null, "gone", emptyList(), 1),
        )
        assertEquals(64000, CartMath.subtotal(lines))
        assertFalse(lines[1].isOrderable)
        assertFalse(lines[2].isOrderable)
    }

    @Test fun local_quote_total_formula() {
        val info = SettingsDto(packagingFeePaise = 1000, taxRateBps = 500, pricesIncludeTax = false, deliveryFeePaise = 3000).toDomain()
        val lines = listOf(CartLine("a", menu[1], "dish_004", emptyList(), 2))
        val pickup = CartMath.localQuote(lines, info, OrderType.PICKUP)
        assertEquals(12000, pickup.subtotalPaise)
        assertEquals(600, pickup.taxPaise)
        assertEquals(12000 + 600 + 1000, pickup.totalPaise)
        val dineIn = CartMath.localQuote(lines, info, OrderType.DINE_IN)
        assertEquals(0, dineIn.packagingFeePaise)
        assertEquals(3000, CartMath.localQuote(lines, info, OrderType.DELIVERY).deliveryFeePaise)
    }

    @Test fun line_key_merges_same_options_regardless_of_order() {
        assertEquals(CartMath.lineKey("x", listOf("b", "a")), CartMath.lineKey("x", listOf("a", "b")))
    }

    @Test fun search_is_case_insensitive_and_filters_work() {
        assertEquals(listOf("dish_007", "dish_011"), MenuFilter.apply(menu, cats, MenuFilters(query = "CHICKEN")).map { it.id })
        assertEquals(2, MenuFilter.apply(menu, cats, MenuFilters(food = FoodFilter.VEG)).size)
        assertEquals(listOf("dish_001", "dish_004"), MenuFilter.apply(menu, cats, MenuFilters(maxPricePaise = 10000)).map { it.id })
        assertEquals("dish_011", MenuFilter.apply(menu, cats, MenuFilters(sort = SortOrder.PRICE_HIGH_LOW)).first().id)
        assertEquals(listOf("dish_004", "dish_007"), MenuFilter.apply(menu, cats, MenuFilters(query = "thali")).map { it.id }) // name + category match
        assertTrue(MenuFilter.apply(menu, cats, MenuFilters(query = "pizza")).isEmpty())
    }

    @Test fun ordering_switches_default_off() {
        val info = SettingsDto().toDomain()
        assertFalse(info.acceptingOrders)
        assertTrue(info.enabledOrderTypes.isEmpty())
    }

    @Test fun status_parsing_is_safe() {
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.parse("SOMETHING_NEW"))
        assertTrue(OrderStatus.PLACED.isActive)
        assertFalse(OrderStatus.COMPLETED.isActive)
    }
}
