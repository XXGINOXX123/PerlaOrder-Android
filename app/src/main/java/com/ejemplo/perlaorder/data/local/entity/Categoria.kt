package com.ejemplo.perlaorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey val id: Int,
    val nombre: String,
    val orden: Int
)
