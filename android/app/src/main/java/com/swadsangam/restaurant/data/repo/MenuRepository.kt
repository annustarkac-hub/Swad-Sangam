package com.swadsangam.restaurant.data.repo

import com.swadsangam.restaurant.BuildConfig
import com.swadsangam.restaurant.data.local.CategoryEntity
import com.swadsangam.restaurant.data.local.MenuDao
import com.swadsangam.restaurant.data.local.MenuItemEntity
import com.swadsangam.restaurant.data.local.OfferEntity
import com.swadsangam.restaurant.data.local.SettingsEntity
import com.swadsangam.restaurant.data.remote.SettingsDto
import com.swadsangam.restaurant.data.remote.SupabaseApi
import com.swadsangam.restaurant.data.remote.foodTypeOf
import com.swadsangam.restaurant.data.remote.toDomain
import com.swadsangam.restaurant.domain.Category
import com.swadsangam.restaurant.domain.ItemOption
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.domain.Offer
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.domain.RestaurantStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
    data object Idle : SyncState
    data object Loading : SyncState
    data object Ok : SyncState
    data class Failed(val offline: Boolean) : SyncState
}

/**
 * Menu, offers and restaurant settings. The UI always reads from the local Room cache
 * (instant, works offline) and [refresh] updates the cache from the server.
 */
@Singleton
class MenuRepository @Inject constructor(
    private val api: SupabaseApi,
    private val dao: MenuDao,
    private val json: Json,
) {
    private val optionsSerializer = ListSerializer(ItemOption.serializer())
    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync
    private val _status = MutableStateFlow<RestaurantStatus?>(null)
    val status: StateFlow<RestaurantStatus?> = _status

    val configured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank()

    val categories: Flow<List<Category>> = dao.categories().map { list ->
        list.map { Category(it.id, it.name, it.nameHi, it.sortOrder) }
    }

    val items: Flow<List<MenuItem>> = dao.items().map { list -> list.map { it.toDomain() } }

    val offers: Flow<List<Offer>> = dao.offers().map { list ->
        list.map { Offer(it.id, it.title, it.description, it.imageUrl, it.couponCode) }
    }

    val restaurant: Flow<RestaurantInfo> = dao.settings().map { e ->
        val dto = e?.let { runCatching { json.decodeFromString(SettingsDto.serializer(), it.json) }.getOrNull() } ?: SettingsDto()
        dto.toDomain()
    }

    suspend fun restaurantNow(): RestaurantInfo {
        val e = dao.settingsNow()
        val dto = e?.let { runCatching { json.decodeFromString(SettingsDto.serializer(), it.json) }.getOrNull() } ?: SettingsDto()
        return dto.toDomain()
    }

    suspend fun itemsNow(): List<MenuItem> = dao.itemsNow().map { it.toDomain() }

    /** Fetches everything from the server. Never throws; returns success. */
    suspend fun refresh(): Boolean {
        if (!configured) {
            _sync.value = SyncState.Failed(offline = false)
            return false
        }
        _sync.value = SyncState.Loading
        return try {
            coroutineScope {
                val s = async { api.settings() }
                val c = async { api.categories() }
                val i = async { api.menuItems() }
                val o = async { runCatching { api.offers() }.getOrDefault(emptyList()) }
                val st = async { runCatching { api.status() }.getOrNull() }
                val settings = s.await().firstOrNull()
                if (settings != null) {
                    dao.upsertSettings(SettingsEntity(json = json.encodeToString(SettingsDto.serializer(), settings), fetchedAt = System.currentTimeMillis()))
                }
                val activeCats = c.await().filter { it.isActive }.map { CategoryEntity(it.id, it.name, it.nameHi, it.sortOrder) }
                val catIds = activeCats.map { it.id }.toSet()
                val items = i.await().filter { !it.isArchived && it.categoryId in catIds }.map {
                    MenuItemEntity(
                        id = it.id, name = it.name, nameHi = it.nameHi, categoryId = it.categoryId,
                        description = it.description, descriptionHi = it.descriptionHi, pricePaise = it.pricePaise,
                        foodType = it.foodType, imageUrl = it.imageUrl, isAvailable = it.isAvailable,
                        isPopular = it.isPopular, preparationMinutes = it.preparationMinutes,
                        allergens = it.allergens, portionInfo = it.portionInfo,
                        optionsJson = json.encodeToString(optionsSerializer, it.options), sortOrder = it.sortOrder,
                    )
                }
                dao.replaceMenu(activeCats, items)
                dao.replaceOffers(o.await().map { OfferEntity(it.id, it.title, it.description, it.imageUrl, it.couponCode, it.sortOrder) })
                st.await()?.let { _status.value = it.toDomain() }
            }
            _sync.value = SyncState.Ok
            true
        } catch (e: java.io.IOException) {
            _sync.value = SyncState.Failed(offline = true)
            false
        } catch (e: Exception) {
            _sync.value = SyncState.Failed(offline = false)
            false
        }
    }

    suspend fun refreshStatus() {
        if (!configured) return
        runCatching { api.status() }.getOrNull()?.let { _status.value = it.toDomain() }
    }

    private fun MenuItemEntity.toDomain() = MenuItem(
        id = id, name = name, nameHi = nameHi, categoryId = categoryId, description = description,
        descriptionHi = descriptionHi, pricePaise = pricePaise, foodType = foodTypeOf(foodType),
        imageUrl = imageUrl, isAvailable = isAvailable, isPopular = isPopular,
        preparationMinutes = preparationMinutes, allergens = allergens, portionInfo = portionInfo,
        options = runCatching { json.decodeFromString(optionsSerializer, optionsJson) }.getOrDefault(emptyList()),
        sortOrder = sortOrder,
    )
}
