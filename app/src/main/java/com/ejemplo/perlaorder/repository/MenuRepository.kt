package com.ejemplo.perlaorder.repository

import android.util.Log
import com.ejemplo.perlaorder.data.local.SemillaCarta
import com.ejemplo.perlaorder.data.local.dao.*
import com.ejemplo.perlaorder.data.local.entity.*
import com.ejemplo.perlaorder.data.network.PerlaApiService
import com.ejemplo.perlaorder.data.network.dto.*
import com.ejemplo.perlaorder.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException

private const val TAG = "MenuRepository"

/** El servidor respondió, pero con un código de error (ej. 500). */
class ErrorHttp(val codigo: Int) : Exception("El servidor respondió HTTP $codigo")

/** No hay sesión del personal, o el servidor rechazó el token (HTTP 401). */
class SesionRequerida : Exception("Se requiere iniciar sesión")

class MenuRepository(
    private val platoDao: PlatoDao,
    private val pedidoDao: PedidoDao,
    private val api: PerlaApiService
) {
    private val cerrojoPedido = kotlinx.coroutines.sync.Mutex()

    suspend fun agregarAlPedido(plato: Plato) = cerrojoPedido.withLock {
        require(plato.activo && !plato.agotado) { "El plato no está disponible." }
        val anterior = pedidoDao.buscarPorPlato(plato.id)
        if (anterior == null) pedidoDao.insertar(PedidoItem(platoId = plato.id, cantidad = 1))
        else if (anterior.cantidad < 99) pedidoDao.actualizarCantidad(anterior.id, anterior.cantidad + 1)
    }

    suspend fun vaciarPedido() = cerrojoPedido.withLock { pedidoDao.vaciar() }

    suspend fun confirmarPedido(mesa: Int, lineas: List<LineaPedido>, clave: String): Result<Long> {
        return try {
            require(mesa in 1..20) { "Seleccione una mesa válida (1 a 20)." }
            require(lineas.isNotEmpty()) { "El carrito está vacío." }
            val solicitud = PedidoCrearDto(clave, mesa, lineas.map {
                PedidoLineaCrearDto(it.platoId, it.cantidad, it.indicaciones)
            })
            val respuesta = api.registrarPedido(solicitud)
            if (!respuesta.isSuccessful || respuesta.body() == null)
                Result.failure(ErrorHttp(respuesta.code()))
            else {
                val id = respuesta.body()!!.id
                // Se limpia únicamente cuando el servidor confirma 200 o 201.
                cerrojoPedido.withLock { pedidoDao.vaciar() }
                Result.success(id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val categorias: Flow<List<Categoria>> = platoDao.observarCategorias()
    val platosActivos: Flow<List<Plato>> = platoDao.observarPlatosActivos()
    val lineasPedido: Flow<List<LineaPedido>> = pedidoDao.observarLineas()

    fun platosDe(categoriaId: Int): Flow<List<Plato>> = platoDao.observarPlatosPorCategoria(categoriaId)

    /**
     * Primer arranque: carga la carta semilla para que la app funcione sin red.
     * Luego intenta traer la carta del servidor; si falla, se queda con la local.
     */
    suspend fun prepararCarta() {
        if (platoDao.contarPlatos() == 0) {
            platoDao.guardarCategorias(SemillaCarta.categorias)
            platoDao.guardarPlatos(SemillaCarta.platos)
        }
        sincronizarCarta()
    }

    // ---- CRUD local de platos ----
    suspend fun agregarPlato(plato: Plato): Long = platoDao.insertar(plato)
    suspend fun actualizarPlato(plato: Plato) = platoDao.actualizar(plato)
    suspend fun darDeBaja(platoId: Int) = platoDao.darDeBaja(platoId)
    suspend fun eliminarDefinitivo(plato: Plato) = platoDao.eliminar(plato)

    // ---- Consumo de la API REST: GET /api/platos (Actividad 08) ----
    /** Devuelve la cantidad de platos recibidos, o el motivo del fallo. */
    suspend fun sincronizarCarta(): Result<Int> {
        return try {
            val respuesta = api.obtenerPlatos()                       // llamada suspendida
            if (respuesta.isSuccessful) {                             // HTTP 200
                val platos = respuesta.body().orEmpty().map { it.aEntidad() }
                platoDao.guardarPlatos(platos)                        // @Upsert: inserta o actualiza
                Log.i(TAG, "GET /api/platos -> HTTP ${respuesta.code()}: ${platos.size} platos guardados en Room")
                Result.success(platos.size)
            } else {                                                  // HTTP 500 u otro error
                Log.e(TAG, "GET /api/platos -> HTTP ${respuesta.code()} (error del servidor)")
                Result.failure(ErrorHttp(respuesta.code()))
            }
        } catch (e: IOException) {
            // Servidor apagado, IP o puerto incorrectos, Wi-Fi caída o tiempo agotado.
            Log.e(TAG, "GET /api/platos -> sin conexión: ${e.message}")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e   // nunca se "traga" la cancelación de una corrutina
        } catch (e: Exception) {
            // JSON con formato inesperado o error al guardar en Room.
            Log.e(TAG, "GET /api/platos -> error inesperado", e)
            Result.failure(e)
        }
    }

    // ---- Sesión del personal: POST /api/v1/auth/login ----
    /** El token vive solo en memoria: al cerrar la app hay que volver a iniciar sesión. */
    @Volatile
    private var tokenSesion: String? = null

    val haySesion: Boolean get() = tokenSesion != null

    suspend fun iniciarSesion(usuario: String, contrasena: String): Result<Unit> {
        return try {
            val respuesta = api.iniciarSesion(LoginRequest(usuario, contrasena))
            val sesion = respuesta.body()
            if (respuesta.isSuccessful && sesion != null) {           // HTTP 200
                tokenSesion = sesion.token
                // Nunca se escribe el token ni la contraseña en el log.
                Log.i(TAG, "POST /api/v1/auth/login -> HTTP ${respuesta.code()}: sesión iniciada como ${sesion.usuario} (${sesion.rol})")
                Result.success(Unit)
            } else {                                                  // 401 credenciales, 429 bloqueo
                Log.e(TAG, "POST /api/v1/auth/login -> HTTP ${respuesta.code()}")
                Result.failure(ErrorHttp(respuesta.code()))
            }
        } catch (e: IOException) {
            Log.e(TAG, "POST /api/v1/auth/login -> sin conexión: ${e.message}")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "POST /api/v1/auth/login -> error inesperado", e)
            Result.failure(e)
        }
    }

    // ---- Alta de platos en el servidor: POST /api/v1/platos ----
    /**
     * Envía el plato al servidor y guarda en Room la versión que el servidor devuelve,
     * con el id que él asignó. Así tablet y servidor nunca usan ids distintos.
     */
    suspend fun registrarPlatoEnServidor(plato: Plato): Result<Plato> {
        val token = tokenSesion ?: return Result.failure(SesionRequerida())
        val solicitud = PlatoCrearRequest(
            categoriaId = plato.categoriaId,
            nombre = plato.nombre,
            precioCentimos = plato.precioCentimos,
            descripcion = plato.descripcion,
            ingredientes = plato.ingredientes,
            alergenos = plato.alergenos,
            porcion = plato.porcion
        )
        return try {
            val respuesta = api.crearPlato("Bearer $token", solicitud)
            val creado = respuesta.body()
            when {
                respuesta.isSuccessful && creado != null -> {         // HTTP 201
                    val entidad = creado.aEntidad()
                    platoDao.guardarPlatos(listOf(entidad))           // @Upsert con el id del servidor
                    Log.i(TAG, "POST /api/v1/platos -> HTTP ${respuesta.code()}: plato ${entidad.id} guardado en Room")
                    Result.success(entidad)
                }
                respuesta.code() == 401 -> {                          // token vencido o servidor reiniciado
                    tokenSesion = null
                    Log.e(TAG, "POST /api/v1/platos -> HTTP 401 (sesión inválida o vencida)")
                    Result.failure(SesionRequerida())
                }
                else -> {                                             // 403 rol, 422 validación, 500...
                    Log.e(TAG, "POST /api/v1/platos -> HTTP ${respuesta.code()}")
                    Result.failure(ErrorHttp(respuesta.code()))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "POST /api/v1/platos -> sin conexión: ${e.message}")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "POST /api/v1/platos -> error inesperado", e)
            Result.failure(e)
        }
    }

    // ---- Actividad 09: edición y baja en el servidor (PUT / DELETE) ----

    /**
     * PUT /api/v1/platos/{id}. Room se actualiza SOLO si el servidor confirma (2xx).
     * Si el servidor no devuelve cuerpo (204), se guarda el plato enviado.
     */
    suspend fun actualizarPlatoEnServidor(plato: Plato): Result<Plato> {
        val respuesta = llamarConSesion("PUT /api/v1/platos/${plato.id}") { autorizacion ->
            api.actualizarPlato(autorizacion, plato.id, plato.aSolicitud())
        }.getOrElse { return Result.failure(it) }
        val entidad = respuesta.body()?.aEntidad() ?: plato
        platoDao.guardarPlatos(listOf(entidad))                       // @Upsert
        return Result.success(entidad)
    }

    /** DELETE /api/v1/platos/{id} (baja lógica). Room marca activo = 0 solo si el servidor confirma. */
    suspend fun darDeBajaEnServidor(platoId: Int): Result<Unit> {
        llamarConSesion("DELETE /api/v1/platos/$platoId") { autorizacion ->
            api.darDeBajaPlato(autorizacion, platoId)
        }.onFailure { return Result.failure(it) }
        platoDao.darDeBaja(platoId)
        return Result.success(Unit)
    }

    /**
     * Ejecuta una llamada autenticada y traduce el resultado:
     *  - 2xx            -> Result.success(respuesta)
     *  - 401            -> SesionRequerida (y se descarta el token)
     *  - otro código    -> ErrorHttp(código)
     *  - sin red / falla -> la excepción (IOException = servidor apagado, Wi-Fi caída, tiempo agotado)
     */
    private suspend fun <T> llamarConSesion(
        etiqueta: String,
        llamada: suspend (autorizacion: String) -> Response<T>
    ): Result<Response<T>> {
        val token = tokenSesion ?: return Result.failure(SesionRequerida())
        return try {
            val respuesta = llamada("Bearer $token")
            when {
                respuesta.isSuccessful -> {
                    Log.i(TAG, "$etiqueta -> HTTP ${respuesta.code()}")
                    Result.success(respuesta)
                }
                respuesta.code() == 401 -> {
                    tokenSesion = null
                    Log.e(TAG, "$etiqueta -> HTTP 401 (sesión inválida o vencida)")
                    Result.failure(SesionRequerida())
                }
                else -> {
                    Log.e(TAG, "$etiqueta -> HTTP ${respuesta.code()}")
                    Result.failure(ErrorHttp(respuesta.code()))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "$etiqueta -> sin conexión: ${e.message}")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$etiqueta -> error inesperado", e)
            Result.failure(e)
        }
    }

    private fun Plato.aSolicitud() = PlatoCrearRequest(
        categoriaId = categoriaId,
        nombre = nombre,
        precioCentimos = precioCentimos,
        descripcion = descripcion,
        ingredientes = ingredientes,
        alergenos = alergenos,
        porcion = porcion
    )

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