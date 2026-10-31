package com.ejemplo.perlaorder.data.network

import com.ejemplo.perlaorder.data.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface PerlaApiService {
    @GET("api/platos")                     // READ
    suspend fun obtenerPlatos(): Response<List<PlatoDto>>

    @POST("api/v1/auth/login")             // pedir el token
    suspend fun iniciarSesion(@Body credenciales: LoginRequest): Response<LoginResponse>

    @POST("api/v1/platos")                 // CREATE
    suspend fun crearPlato(@Header("Authorization") autorizacion: String, @Body plato: PlatoCrearRequest): Response<PlatoDto>

    @PUT("api/v1/platos/{id}")             // UPDATE
    suspend fun actualizarPlato(@Header("Authorization") autorizacion: String, @Path("id") id: Int, @Body plato: PlatoCrearRequest): Response<PlatoDto>

    @DELETE("api/v1/platos/{id}")          // DELETE (baja lógica)
    suspend fun darDeBajaPlato(@Header("Authorization") autorizacion: String, @Path("id") id: Int): Response<Unit>

    @POST("api/v1/pedidos")
    suspend fun registrarPedido(@Body pedido: PedidoCrearDto): Response<PedidoRegistradoDto>

    @POST("api/comandas")                  // se integra en un sprint posterior
    suspend fun enviarComanda(@Body comanda: ComandaRequest): Response<Unit>
}