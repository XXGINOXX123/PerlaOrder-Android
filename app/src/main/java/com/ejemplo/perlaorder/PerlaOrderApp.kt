package com.ejemplo.perlaorder

import android.app.Application
import com.ejemplo.perlaorder.data.local.AppDatabase
import com.ejemplo.perlaorder.data.network.RetrofitClient
import com.ejemplo.perlaorder.repository.MenuRepository

class PerlaOrderApp : Application() {
    val database by lazy { AppDatabase.obtener(this) }
    val menuRepository by lazy {
        MenuRepository(database.platoDao(), database.pedidoDao(), RetrofitClient.api)
    }
}