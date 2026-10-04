package com.ejemplo.perlaorder.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: PerlaApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(okHttp)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(PerlaApiService::class.java)
    }
}