package com.ejemplo.perlaorder.data.local.entity

import androidx.room.*

@Entity(
    tableName = "platos",
    foreignKeys = [ForeignKey(entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoria_id"])],
    indices = [Index("categoria_id")]
)
data class Plato(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "categoria_id") val categoriaId: Int,
    val nombre: String,
    val descripcion: String,
    val ingredientes: String,
    val alergenos: String,
    val porcion: String,
    @ColumnInfo(name = "precio_centimos") val precioCentimos: Long,
    @ColumnInfo(name = "imagen_url") val imagenUrl: String? = null,
    val agotado: Boolean = false,
    val activo: Boolean = true
)