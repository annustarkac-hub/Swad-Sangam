package com.swadsangam.restaurant.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swadsangam.restaurant.data.local.OrderDao
import com.swadsangam.restaurant.data.remote.AddressDto
import com.swadsangam.restaurant.data.remote.AddressWrite
import com.swadsangam.restaurant.data.remote.PasswordLogin
import com.swadsangam.restaurant.data.remote.ProfileDto
import com.swadsangam.restaurant.data.remote.RecoverBody
import com.swadsangam.restaurant.data.remote.RefreshBody
import com.swadsangam.restaurant.data.remote.SessionDto
import com.swadsangam.restaurant.data.remote.SignUpBody
import com.swadsangam.restaurant.data.remote.SupabaseApi
import com.swadsangam.restaurant.data.remote.TokensBody
import com.swadsangam.restaurant.data.prefs.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore by preferencesDataStore("customer_auth")

data class Account(val email: String, val profile: ProfileDto?, val addresses: List<AddressDto>)

sealed interface AuthResult {
    data object SignedIn : AuthResult
    /** Account made, but Supabase wants the email confirmed before the first login. */
    data object ConfirmEmail : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Optional customer account (free Supabase email + password login — no SMS/OTP).
 * Guests can still order exactly as before; signing in only adds saved details, addresses and
 * order history on any phone. Staff access is unaffected: a customer account has no staff role.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SupabaseApi,
    private val holder: SessionHolder,
    private val orderDao: OrderDao,
    private val prefs: UserPrefs,
    private val json: Json,
) {
    private val ACCESS = stringPreferencesKey("access")
    private val REFRESH = stringPreferencesKey("refresh")
    private val EXPIRES = longPreferencesKey("expires_at")
    private val USER = stringPreferencesKey("user_id")
    private val EMAIL = stringPreferencesKey("email")

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()
    private var restored = false

    /** Loads the saved session (once) and refreshes it if needed. Returns true when signed in. */
    suspend fun ensureSession(): Boolean {
        if (!restored) {
            restored = true
            val p = context.authStore.data.first()
            val a = p[ACCESS]; val r = p[REFRESH]; val u = p[USER]
            if (a != null && r != null && u != null) holder.session = AuthSession(a, r, p[EXPIRES] ?: 0, u, p[EMAIL].orEmpty())
        }
        val s = holder.session ?: return false
        if (s.isFresh()) return true
        return try {
            save(api.refreshSession(RefreshBody(s.refreshToken)), s.email)
            true
        } catch (e: HttpException) {
            if (e.code() in 400..403) clearSession() // refresh token revoked/expired → signed out
            false
        } catch (e: Exception) {
            false // offline: keep the session for later
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = call {
        save(api.signIn(PasswordLogin(email.trim().lowercase(), password)), email.trim().lowercase())
        afterSignIn(); AuthResult.SignedIn
    }

    suspend fun signUp(email: String, password: String, name: String, phone: String): AuthResult = call {
        val e = email.trim().lowercase()
        val s = api.signUp(SignUpBody(e, password, mapOf("account_type" to "customer", "full_name" to name.trim(), "phone" to phone)))
        if (s.accessToken == null) AuthResult.ConfirmEmail else {
            save(s, e)
            runCatching { api.saveProfile(ProfileDto(holder.session!!.userId, name.trim(), phone)) }
            afterSignIn(); AuthResult.SignedIn
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult = call {
        api.recover(RecoverBody(email.trim().lowercase())); AuthResult.SignedIn
    }

    suspend fun signOut() {
        runCatching { if (holder.bearer() != null) api.logout() }
        clearSession()
    }

    /** Reloads profile + addresses (no-op for guests). */
    suspend fun reload() {
        if (!ensureSession()) { _account.value = null; return }
        val s = holder.session ?: return
        val profile = runCatching { api.profile().firstOrNull() }.getOrNull()
        val addresses = runCatching { api.addresses() }.getOrDefault(_account.value?.addresses.orEmpty())
        _account.value = Account(s.email, profile ?: _account.value?.profile, addresses)
        profile?.let { prefs.saveCustomer(it.fullName, it.phone) }
    }

    suspend fun saveProfile(name: String, phone: String): Result<Unit> = runCatching {
        check(ensureSession())
        api.saveProfile(ProfileDto(holder.session!!.userId, name.trim(), phone))
        reload()
    }

    suspend fun saveAddress(id: Long?, a: AddressWrite): Result<Unit> = runCatching {
        check(ensureSession())
        val r = if (id == null) api.addAddress(a) else api.updateAddress("eq.$id", a)
        if (!r.isSuccessful) error(errorMessage(r.errorBody()?.string()))
        reload()
    }

    suspend fun deleteAddress(id: Long): Result<Unit> = runCatching {
        check(ensureSession()); api.deleteAddress("eq.$id"); reload()
    }

    /** Saves account orders into the on-phone order list so "My orders" shows them on any phone. */
    suspend fun syncOrders(save: suspend (com.swadsangam.restaurant.data.remote.OrderDto) -> Unit) {
        if (!ensureSession()) return
        runCatching { api.myOrders() }.getOrNull()?.forEach { save(it) }
    }

    private suspend fun afterSignIn() {
        // attach orders this phone placed as a guest
        val tokens = orderDao.allNow().map { it.accessToken }.take(50)
        if (tokens.isNotEmpty()) runCatching { api.claimOrders(TokensBody(tokens)) }
        reload()
    }

    private suspend fun save(s: SessionDto, email: String) {
        val access = s.accessToken ?: error("No session")
        val exp = s.expiresAt ?: (System.currentTimeMillis() / 1000 + (s.expiresIn ?: 3600))
        val session = AuthSession(access, s.refreshToken.orEmpty(), exp, s.user?.id ?: s.id.orEmpty(), s.user?.email ?: email)
        holder.session = session
        context.authStore.edit {
            it[ACCESS] = session.accessToken; it[REFRESH] = session.refreshToken; it[EXPIRES] = session.expiresAtSec
            it[USER] = session.userId; it[EMAIL] = session.email
        }
    }

    private suspend fun clearSession() {
        holder.session = null
        _account.value = null
        context.authStore.edit { it.clear() }
    }

    private suspend fun call(block: suspend () -> AuthResult): AuthResult = try {
        block()
    } catch (e: HttpException) {
        AuthResult.Error(errorMessage(e.response()?.errorBody()?.string()))
    } catch (e: java.io.IOException) {
        AuthResult.Error("NETWORK")
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Error")
    }

    private fun errorMessage(body: String?): String {
        val o = runCatching { json.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull() ?: return "Error"
        return listOf("error_code", "msg", "error_description", "message").firstNotNullOfOrNull { k -> o[k]?.jsonPrimitive?.content } ?: "Error"
    }
}
