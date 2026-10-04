package com.ejemplo.perlaorder.repository

import com.ejemplo.perlaorder.data.local.SemillaCarta
import com.ejemplo.perlaorder.data.local.dao.*
import com.ejemplo.perlaorder.data.local.entity.*
import com.ejemplo.perlaorder.data.network.PerlaApiService
import com.ejemplo.perlaorder.data.network.dto.*
import com.ejemplo.perlaorder.model.*
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class MenuRepository(
    private val platoDao: PlatoDao,
    private val pedidoDao: PedidoDao,
    private val api: PerlaApiService
) {
    val categorias: Flow<List<Categoria>> = platoDao.observarCategorias()
    val platosActivos: Flow<List<Plato>> = platoDao.observarPlatosActivos()
    val lineasPedido: Flow<List<LineaPedido>> = pedidoDao.observarLineas()

    fun platosDe(categoriaId: Int): Flow<List<Plato>> = platoDao.observarPlatosPorCategoria(categoriaId)

    suspend fun prepararCarta() {
        if (platoDao.contarPlatos() == 0) {
            platoDao.guardarCategorias(SemillaCarta.categorias)
            platoDao.guardarPlatos(SemillaCarta.platos)
        }
        try {
            platoDao.guardarCategorias(api.obtenerCategorias())
            platoDao.guardarPlatos(api.obtenerPlatos())
        } catch (e: Exception) { /* Mantiene operación offline garantizada */ }
    }

    // ---- CRUD de platos ----
    suspend fun agregarPlato(plato: Plato): Long = platoDao.insertar(plato)
    suspend fun actualizarPlato(plato: Plato) = platoDao.actualizar(plato)
    suspend fun darDeBaja(platoId: Int) = platoDao.darDeBaja(platoId)
    suspend fun eliminarDefinitivo(plato: Plato) = platoDao.eliminar(plato)

    suspend fun sincronizarCarta(): Boolean = try {
        platoDao.guardarCategorias(api.obtenerCategorias())
        platoDao.guardarPlatos(api.obtenerPlatos())
        true
    } catch (e: Exception) {
        false
    }

    // ---- Pedido (se usará en sprints posteriores) ----
    suspend fun agregarAlPedido(plato: Plato, cantidad: Int, indicaciones: String) {
        require(!plato.agotado) { "Plato agotado" }
        pedidoDao.insertar(PedidoItem(platoId = plato.id, cantidad = cantidad, indicacionesEspeciales = indicaciones.trim()))
    }

    suspend fun cambiarCantidad(itemId: Long, nuevaCantidad: Int) {
        if (nuevaCantidad <= 0) pedidoDao.eliminar(itemId) else pedidoDao.actualizarCantidad(itemId, nuevaCantidad)
    }

    suspend fun enviarComanda(comandaId: String, mesa: Int, lineas: List<LineaPedido>): Result<Unit> {
        val request = ComandaRequest(
            comandaId, mesa,
            lineas.map { ComandaItemDto(it.platoId, it.nombre, it.cantidad, it.precioUnitarioCentimos, it.indicaciones) },
            CalculadoraPedido.total(lineas), System.currentTimeMillis()
        )
        return try {
            val res = api.enviarComanda(request)
            if (res.isSuccessful) { pedidoDao.vaciar(); Result.success(Unit) }
            else Result.failure(Exception("Error en servidor"))
        } catch (e: IOException) { Result.failure(e) }
    }
}