package com.swadsangam.restaurant.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Supabase PostgREST API. Public endpoints only — the app never holds admin credentials
 * (PRD §9.3). Mapping to the PRD's REST design (§11):
 *   GET  /v1/restaurant           → restaurant_settings
 *   GET  /v1/menu/categories      → categories
 *   GET  /v1/menu/items           → menu_items
 *   POST /v1/orders               → rpc/place_order
 *   GET  /v1/orders/{id}          → rpc/get_order_by_token
 *   POST /v1/orders/{id}/cancel   → rpc/cancel_order_by_customer
 */
interface SupabaseApi {
    @GET("rest/v1/restaurant_settings")
    suspend fun settings(@Query("select") select: String = "*", @Query("id") id: String = "eq.1"): List<SettingsDto>

    @GET("rest/v1/categories")
    suspend fun categories(@Query("select") select: String = "*", @Query("order") order: String = "sort_order"): List<CategoryDto>

    @GET("rest/v1/menu_items")
    suspend fun menuItems(@Query("select") select: String = "*", @Query("order") order: String = "sort_order"): List<MenuItemDto>

    @GET("rest/v1/offers")
    suspend fun offers(@Query("select") select: String = "*", @Query("order") order: String = "sort_order"): List<OfferDto>

    @POST("rest/v1/rpc/restaurant_status")
    suspend fun status(@Body body: Map<String, String> = emptyMap()): StatusDto

    @POST("rest/v1/rpc/quote_order")
    suspend fun quote(@Body body: QuoteBody): QuoteDto

    @POST("rest/v1/rpc/place_order")
    suspend fun placeOrder(@Body body: PlaceOrderBody): RpcResult

    @POST("rest/v1/rpc/get_order_by_token")
    suspend fun order(@Body body: TokenBody): kotlinx.serialization.json.JsonElement // order object or JSON null

    @POST("rest/v1/rpc/get_orders_by_tokens")
    suspend fun orders(@Body body: TokensBody): List<OrderDto>

    @POST("rest/v1/rpc/cancel_order_by_customer")
    suspend fun cancel(@Body body: CancelBody): RpcResult

    @POST("rest/v1/rpc/set_order_push_token")
    suspend fun setPushToken(@Body body: PushTokenBody): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/submit_feedback")
    suspend fun submitFeedback(@Body body: FeedbackBody): RpcResult

    @POST("rest/v1/rpc/log_events")
    suspend fun logEvents(@Body body: EventsBody): retrofit2.Response<Unit>
}
