package com.ejemplo.perlaorder.data.local.dao

import androidx.room.*
import com.ejemplo.perlaorder.data.local.entity.PedidoItem
import com.ejemplo.perlaorder.model.LineaPedido
import kotlinx.coroutines.flow.Flow

@Dao
interface PedidoDao {
    @Query("SELECT * FROM pedido_items WHERE plato_id = :platoId LIMIT 1")
    suspend fun buscarPorPlato(platoId: Int): PedidoItem?

    @Insert
    suspend fun insertar(item: PedidoItem): Long

    @Query("UPDATE pedido_items SET cantidad = :cantidad WHERE id = :itemId")
    suspend fun actualizarCantidad(itemId: Long, cantidad: Int)

    @Query("DELETE FROM pedido_items WHERE id = :itemId")
    suspend fun eliminar(itemId: Long)

    @Query("DELETE FROM pedido_items")
    suspend fun vaciar()

    @Query("""
        SELECT pi.id AS itemId, pi.plato_id AS platoId, p.nombre AS nombre,
               p.precio_centimos AS precioUnitarioCentimos, pi.cantidad AS cantidad,
               pi.indicaciones_especiales AS indicaciones
        FROM pedido_items AS pi INNER JOIN platos AS p ON p.id = pi.plato_id
        ORDER BY pi.id
    """)
    fun observarLineas(): Flow<List<LineaPedido>>
}