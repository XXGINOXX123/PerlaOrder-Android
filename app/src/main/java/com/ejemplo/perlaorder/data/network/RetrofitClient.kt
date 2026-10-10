package com.ejemplo.perlaorder.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 10.0.2.2 = el "localhost" de tu PC visto desde el EMULADOR.
    // En una tablet física se usa la IP de la PC en la Wi-Fi (ej. 192.168.1.50).
    private const val BASE_URL = "http://10.0.2.2:8080/"

    /** Etiqueta para filtrar en Logcat: tag:PerlaAPI */
    const val TAG_HTTP = "PerlaAPI"

    // Imprime en Logcat la petición y la respuesta completas (cabeceras + JSON).
    private val logging = HttpLoggingInterceptor { mensaje -> Log.d(TAG_HTTP, mensaje) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")   // el token no se escribe en Logcat
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)   // servidor apagado: falla rápido
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val api: PerlaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())  // JSON <-> objetos Kotlin
            .build()
            .create(PerlaApiService::class.java)
    }
}