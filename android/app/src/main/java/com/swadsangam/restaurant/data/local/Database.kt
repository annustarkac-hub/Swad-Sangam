package com.swadsangam.restaurant.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ---------- Offline menu cache (PRD §13: menu available offline after first sync) ----------

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameHi: String?,
    val sortOrder: Int,
)

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameHi: String?,
    val categoryId: String,
    val description: String?,
    val descriptionHi: String?,
    val pricePaise: Int,
    val foodType: String,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val isPopular: Boolean,
    val preparationMinutes: Int?,
    val allergens: String?,
    val portionInfo: String?,
    val optionsJson: String,
    val sortOrder: Int,
)

@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val couponCode: String?,
    val sortOrder: Int,
)

/** Restaurant settings cached as JSON (single row). */
@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val id: Int = 1, val json: String, val fetchedAt: Long)

// ---------- Cart (survives app restarts — FR-06) ----------

@Entity(tableName = "cart_lines")
data class CartLineEntity(
    @PrimaryKey val key: String,
    val itemId: String,
    val optionIdsCsv: String,
    val quantity: Int,
    val addedAt: Long,
)

// ---------- Orders placed from this device (guest order history) ----------

@Entity(tableName = "my_orders")
data class MyOrderEntity(
    @PrimaryKey val accessToken: String,
    val orderId: String,
    val orderNumber: String,
    val status: String,
    val totalPaise: Int,
    val createdAt: String,
    val itemsSummary: String,
    val lastJson: String,
    val notifiedStatus: String,
    val feedbackGiven: Boolean = false,
)

@Dao
interface MenuDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM menu_items ORDER BY sortOrder, name")
    fun items(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items")
    suspend fun itemsNow(): List<MenuItemEntity>

    @Query("SELECT * FROM offers ORDER BY sortOrder")
    fun offers(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settingsNow(): SettingsEntity?

    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM menu_items") suspend fun clearItems()
    @Query("DELETE FROM offers") suspend fun clearOffers()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(c: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(i: List<MenuItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOffers(o: List<OfferEntity>)
    @Upsert suspend fun upsertSettings(s: SettingsEntity)

    @Transaction
    suspend fun replaceMenu(c: List<CategoryEntity>, i: List<MenuItemEntity>) {
        clearCategories(); insertCategories(c)
        clearItems(); insertItems(i)
    }

    @Transaction
    suspend fun replaceOffers(o: List<OfferEntity>) {
        clearOffers(); insertOffers(o)
    }
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_lines ORDER BY addedAt")
    fun lines(): Flow<List<CartLineEntity>>

    @Query("SELECT * FROM cart_lines WHERE `key` = :key")
    suspend fun line(key: String): CartLineEntity?

    @Upsert suspend fun upsert(line: CartLineEntity)
    @Query("DELETE FROM cart_lines WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM cart_lines") suspend fun clear()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM my_orders ORDER BY createdAt DESC")
    fun all(): Flow<List<MyOrderEntity>>

    @Query("SELECT * FROM my_orders ORDER BY createdAt DESC")
    suspend fun allNow(): List<MyOrderEntity>

    @Query("SELECT * FROM my_orders WHERE accessToken = :token")
    fun byToken(token: String): Flow<MyOrderEntity?>

    @Query("SELECT * FROM my_orders WHERE accessToken = :token")
    suspend fun byTokenNow(token: String): MyOrderEntity?

    @Upsert suspend fun upsert(o: MyOrderEntity)
    @Query("DELETE FROM my_orders") suspend fun clear()
}

@Database(
    entities = [CategoryEntity::class, MenuItemEntity::class, OfferEntity::class, SettingsEntity::class,
        CartLineEntity::class, MyOrderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
}
