package com.swadsangam.restaurant.data.auth

import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in customer's tokens, read by the HTTP interceptor. Null = guest (anon key only). */
data class AuthSession(val accessToken: String, val refreshToken: String, val expiresAtSec: Long, val userId: String, val email: String) {
    fun isFresh(nowSec: Long = System.currentTimeMillis() / 1000) = nowSec < expiresAtSec - 60
}

@Singleton
class SessionHolder @Inject constructor() {
    @Volatile var session: AuthSession? = null

    /** Bearer token for API calls: the customer's token while valid, otherwise none (anon). */
    fun bearer(): String? = session?.takeIf { it.isFresh() }?.accessToken
}
