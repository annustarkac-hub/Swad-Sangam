package com.swadsangam.restaurant.di

import android.content.Context
import androidx.room.Room
import com.swadsangam.restaurant.BuildConfig
import com.swadsangam.restaurant.data.local.AppDatabase
import com.swadsangam.restaurant.data.remote.SupabaseApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides @Singleton
    fun okHttp(): OkHttpClient {
        val key = BuildConfig.SUPABASE_ANON_KEY
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("apikey", key)
                    .header("Accept", "application/json")
                // Legacy anon keys are JWTs and also go in Authorization; new publishable keys do not.
                if (key.startsWith("eyJ")) req.header("Authorization", "Bearer $key")
                chain.proceed(req.build())
            }
        if (BuildConfig.DEBUG) {
            // Headers only — never log personal data in request/response bodies (PRD §15.3)
            builder.addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): SupabaseApi {
        val base = BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.invalid" }.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)
    }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "swad_sangam.db")
            .fallbackToDestructiveMigration(dropAllTables = true) // only cached data; orders re-sync by token
            .build()

    @Provides fun menuDao(db: AppDatabase) = db.menuDao()
    @Provides fun cartDao(db: AppDatabase) = db.cartDao()
    @Provides fun orderDao(db: AppDatabase) = db.orderDao()
}
