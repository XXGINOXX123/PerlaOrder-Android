package com.ejemplo.perlaorder.data.local.entity

import androidx.room.*

@Entity(
    tableName = "pedido_items",
    foreignKeys = [ForeignKey(entity = Plato::class, parentColumns = ["id"], childColumns = ["plato_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plato_id")]
)
data class PedidoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plato_id") val platoId: Int,
    val cantidad: Int,
    @ColumnInfo(name = "indicaciones_especiales") val indicacionesEspeciales: String = ""
)