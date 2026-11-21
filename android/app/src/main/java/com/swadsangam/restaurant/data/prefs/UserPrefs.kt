package com.swadsangam.restaurant.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("user_prefs")

data class SavedCustomer(val name: String, val phone: String)

/** Stored only on this device, to pre-fill checkout. Never uploaded except with an order. */
@Singleton
class UserPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val NAME = stringPreferencesKey("name")
    private val PHONE = stringPreferencesKey("phone")
    private val PENDING_KEY = stringPreferencesKey("pending_idempotency_key")
    private val ASKED_NOTIF = booleanPreferencesKey("asked_notification_permission")
    private val FCM = stringPreferencesKey("fcm_token")
    private val PURE_VEG = booleanPreferencesKey("pure_veg")

    /** "Pure Veg Mode": show only vegetarian dishes on Home and Menu. */
    val pureVeg: Flow<Boolean> = context.dataStore.data.map { it[PURE_VEG] ?: false }
    suspend fun setPureVeg(on: Boolean) { context.dataStore.edit { it[PURE_VEG] = on } }

    val customer: Flow<SavedCustomer> = context.dataStore.data.map {
        SavedCustomer(it[NAME].orEmpty(), it[PHONE].orEmpty())
    }

    suspend fun saveCustomer(name: String, phone: String) {
        context.dataStore.edit { it[NAME] = name; it[PHONE] = phone }
    }

    /** One idempotency key per checkout attempt; reused on retry so duplicates are impossible. */
    suspend fun pendingIdempotencyKey(): String? = context.dataStore.data.first()[PENDING_KEY]
    suspend fun setPendingIdempotencyKey(key: String?) {
        context.dataStore.edit { if (key == null) it.remove(PENDING_KEY) else it[PENDING_KEY] = key }
    }

    suspend fun askedNotificationPermission(): Boolean = context.dataStore.data.first()[ASKED_NOTIF] ?: false
    suspend fun setAskedNotificationPermission() { context.dataStore.edit { it[ASKED_NOTIF] = true } }

    suspend fun fcmToken(): String? = context.dataStore.data.first()[FCM]
    suspend fun setFcmToken(token: String) { context.dataStore.edit { it[FCM] = token } }

    suspend fun clearAll() { context.dataStore.edit { it.clear() } }
}
