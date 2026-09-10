package com.jagtarapvtltd.tryzonai.network

import android.content.Context
import android.provider.Settings
import com.jagtarapvtltd.tryzonai.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    const val BASE_URL = BuildConfig.BASE_URL 
    
    private var sessionId: String = java.util.UUID.randomUUID().toString()
    private var authToken: String? = null
    @Volatile
    private var deviceId: String = ""

    fun initSession(context: Context) {
        val prefs = context.getSharedPreferences("TryZonPrefs", Context.MODE_PRIVATE)
        var id = prefs.getString("session_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("session_id", id).apply()
        }
        sessionId = id

        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty()) {
                deviceId = androidId
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        requestBuilder.addHeader("X-Session-ID", sessionId)
        if (deviceId.isNotEmpty()) {
            requestBuilder.addHeader("X-Device-ID", deviceId)
        }
        authToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            addInterceptor(logging)
        }
        connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
    }.build()

    val apiService: TryZonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TryZonApiService::class.java)
    }
}
