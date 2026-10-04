package com.ejemplo.perlaorder.data.local

import android.content.Context
import androidx.room.*
import com.ejemplo.perlaorder.data.local.dao.*
import com.ejemplo.perlaorder.data.local.entity.*

@Database(entities = [Categoria::class, Plato::class, PedidoItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun platoDao(): PlatoDao
    abstract fun pedidoDao(): PedidoDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null
        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "perlaorder.db"
                ).build().also { instancia = it }
            }
    }
}