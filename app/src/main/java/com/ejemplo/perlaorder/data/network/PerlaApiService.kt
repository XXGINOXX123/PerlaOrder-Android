package com.ejemplo.perlaorder.data.network

import com.ejemplo.perlaorder.data.local.entity.*
import com.ejemplo.perlaorder.data.network.dto.ComandaRequest
import retrofit2.Response
import retrofit2.http.*

interface PerlaApiService {
    @GET("api/categorias") suspend fun obtenerCategorias(): List<Categoria>
    @GET("api/platos") suspend fun obtenerPlatos(): List<Plato>
    @POST("api/comandas") suspend fun enviarComanda(@Body comanda: ComandaRequest): Response<Unit>
}