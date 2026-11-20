package com.swadsangam.restaurant.data.repo

import com.swadsangam.restaurant.data.local.CartDao
import com.swadsangam.restaurant.data.local.CartLineEntity
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.CartMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val dao: CartDao,
    menu: MenuRepository,
) {
    /** Cart lines resolved against the latest cached menu (current prices + availability). */
    val lines: Flow<List<CartLine>> = combine(dao.lines(), menu.items) { lines, items ->
        val byId = items.associateBy { it.id }
        lines.map { l ->
            CartLine(
                key = l.key,
                item = byId[l.itemId],
                itemId = l.itemId,
                optionIds = l.optionIdsCsv.split(",").filter { it.isNotBlank() },
                quantity = l.quantity,
            )
        }
    }

    val count: Flow<Int> = dao.lines().map { l -> l.sumOf { it.quantity } }

    suspend fun add(itemId: String, optionIds: List<String>, quantity: Int, max: Int) {
        val key = CartMath.lineKey(itemId, optionIds)
        val existing = dao.line(key)
        val q = ((existing?.quantity ?: 0) + quantity).coerceIn(1, max)
        dao.upsert(CartLineEntity(key, itemId, optionIds.sorted().joinToString(","), q, existing?.addedAt ?: System.currentTimeMillis()))
    }

    suspend fun setQuantity(key: String, quantity: Int, max: Int) {
        val existing = dao.line(key) ?: return
        if (quantity <= 0) dao.delete(key) else dao.upsert(existing.copy(quantity = quantity.coerceAtMost(max)))
    }

    suspend fun remove(key: String) = dao.delete(key)
    suspend fun clear() = dao.clear()
}
