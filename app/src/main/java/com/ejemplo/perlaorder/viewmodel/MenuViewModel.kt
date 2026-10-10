package com.ejemplo.perlaorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.perlaorder.data.local.entity.*
import com.ejemplo.perlaorder.repository.ErrorHttp
import com.ejemplo.perlaorder.repository.MenuRepository
import com.ejemplo.perlaorder.repository.SesionRequerida
import com.ejemplo.perlaorder.util.ValidadorPlato
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * SOLO PARA TOMAR LA CAPTURA DEL ESTADO DE CARGA (Actividad 09).
 * El servidor local responde en milisegundos y el indicador casi no se ve.
 * Para la captura: cambiar a 3000L, ejecutar, capturar y VOLVER A 0L antes del commit final.
 */
private const val RETARDO_CAPTURA_MS = 3000L

/**
 * Estado de cualquier operación contra el servidor (alta, edición, baja, actualizar carta).
 *
 *   Inactivo -> Cargando -> Exito | Falla -> (la pantalla muestra el Snackbar) -> Inactivo
 */
sealed interface EstadoOperacion {
    /** No hay operación en curso ni mensaje pendiente de mostrar. */
    data object Inactivo : EstadoOperacion

    /** Hay una petición en vuelo: la pantalla muestra CircularProgressIndicator. */
    data class Cargando(val detalle: String) : EstadoOperacion

    /** El servidor confirmó: la pantalla muestra un Snackbar normal. */
    data class Exito(val mensaje: String) : EstadoOperacion

    /** Falla de conexión o respuesta de error: la pantalla muestra un Snackbar rojo. */
    data class Falla(val mensaje: String) : EstadoOperacion
}

data class MenuUiState(
    val categorias: List<Categoria> = emptyList(),
    val platos: List<Plato> = emptyList(),
    val categoriaSeleccionada: Int? = null,   // null = "Todas"
    val mensajeError: String? = null,         // error de validación del formulario (no de red)
    val operacion: EstadoOperacion = EstadoOperacion.Inactivo,
    val mostrarLogin: Boolean = false,        // true = se muestra el diálogo «Acceso del personal»
    val errorLogin: String? = null,
    val enviandoLogin: Boolean = false,       // true = el inicio de sesión está en curso
    val platosRegistrados: Int = 0            // sube en 1 por cada alta exitosa; la UI limpia el formulario
) {
    val cargando: Boolean get() = operacion is EstadoOperacion.Cargando
}

/** Estado del diálogo de acceso y del contador de altas. */
private data class EstadoGestion(
    val mostrarLogin: Boolean = false,
    val errorLogin: String? = null,
    val enviandoLogin: Boolean = false,
    val platosRegistrados: Int = 0
)

class MenuViewModel(private val repositorio: MenuRepository) : ViewModel() {

    private val categoriaSeleccionada = MutableStateFlow<Int?>(null)
    private val mensajeError = MutableStateFlow<String?>(null)
    private val operacion = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    private val gestion = MutableStateFlow(EstadoGestion())

    val uiState: StateFlow<MenuUiState> = combine(
        repositorio.categorias, repositorio.platosActivos,
        categoriaSeleccionada, mensajeError, operacion
    ) { categorias, platos, seleccion, error, op ->
        MenuUiState(
            categorias = categorias,
            platos = if (seleccion == null) platos else platos.filter { it.categoriaId == seleccion },
            categoriaSeleccionada = seleccion,
            mensajeError = error,
            operacion = op
        )
    }.combine(gestion) { estado, g ->
        estado.copy(
            mostrarLogin = g.mostrarLogin,
            errorLogin = g.errorLogin,
            enviandoLogin = g.enviandoLogin,
            platosRegistrados = g.platosRegistrados
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuUiState())

    init { viewModelScope.launch { repositorio.prepararCarta() } }

    fun seleccionarCategoria(id: Int?) { categoriaSeleccionada.value = id }

    /** La pantalla ya mostró el Snackbar de Exito/Falla: vuelve a Inactivo. */
    fun operacionAtendida() {
        if (operacion.value is EstadoOperacion.Exito || operacion.value is EstadoOperacion.Falla) {
            operacion.value = EstadoOperacion.Inactivo
        }
    }

    /**
     * Único punto donde una operación de red pasa por los tres estados:
     * Cargando (mientras dura la petición) y luego Exito o Falla (lo que devuelva [bloque]).
     */
    private fun ejecutar(detalle: String, bloque: suspend () -> EstadoOperacion) {
        if (operacion.value is EstadoOperacion.Cargando) return   // evita doble toque
        operacion.value = EstadoOperacion.Cargando(detalle)
        viewModelScope.launch {
            delay(RETARDO_CAPTURA_MS)                              // 0 ms salvo para la captura
            operacion.value = bloque()
        }
    }

    /** Convierte la excepción del repositorio en un mensaje para el usuario. */
    private fun falla(error: Throwable, accion: String): EstadoOperacion.Falla {
        val mensaje = when {
            error is SesionRequerida -> {
                gestion.update { it.copy(mostrarLogin = true, errorLogin = null) }
                "La sesión venció. Inicie sesión nuevamente."
            }
            error is ErrorHttp && error.codigo == 403 -> "Su usuario no tiene permiso para $accion."
            error is ErrorHttp && error.codigo == 404 -> "El plato no existe en el servidor (HTTP 404). Pulse «Actualizar carta»."
            error is ErrorHttp && error.codigo == 422 -> "El servidor rechazó los datos del plato. Revise el nombre (máximo 80 caracteres) y el precio."
            error is ErrorHttp -> "El servidor respondió con error (HTTP ${error.codigo}). No se pudo $accion."
            else -> "Sin conexión con el servidor del restaurante. No se pudo $accion."
        }
        return EstadoOperacion.Falla(mensaje)
    }

    // ---- READ: GET /api/platos ----
    fun actualizarCarta() {
        ejecutar("Actualizando carta...") {
            repositorio.sincronizarCarta().fold(
                onSuccess = { cantidad ->
                    EstadoOperacion.Exito("Carta actualizada desde el servidor: $cantidad platos recibidos.")
                },
                onFailure = { error ->
                    EstadoOperacion.Falla(
                        if (error is ErrorHttp)
                            "El servidor respondió con error (HTTP ${error.codigo}). Se muestra la última carta guardada."
                        else
                            "Sin conexión con el servidor del restaurante. Se muestra la última carta guardada en el dispositivo."
                    )
                }
            )
        }
    }

    // ---- Sesión del personal ----
    fun cerrarLogin() { gestion.update { it.copy(mostrarLogin = false, errorLogin = null) } }

    fun iniciarSesion(usuario: String, contrasena: String) {
        if (usuario.isBlank() || contrasena.isEmpty()) {
            gestion.update { it.copy(errorLogin = "Ingrese su usuario y su contraseña.") }
            return
        }
        gestion.update { it.copy(enviandoLogin = true, errorLogin = null) }
        viewModelScope.launch {
            repositorio.iniciarSesion(usuario.trim(), contrasena)
                .onSuccess {
                    gestion.update { it.copy(enviandoLogin = false, mostrarLogin = false, errorLogin = null) }
                    operacion.value = EstadoOperacion.Exito("Sesión iniciada. Ya puede guardar, editar o dar de baja platos en el servidor.")
                }
                .onFailure { error ->
                    val mensaje = when {
                        error is ErrorHttp && error.codigo == 401 -> "Usuario o contraseña incorrectos."
                        error is ErrorHttp && error.codigo == 429 -> "Demasiados intentos fallidos. Espere unos minutos y vuelva a intentar."
                        error is ErrorHttp -> "El servidor respondió con error (HTTP ${error.codigo})."
                        else -> "Sin conexión con el servidor del restaurante."
                    }
                    gestion.update { it.copy(enviandoLogin = false, errorLogin = mensaje) }
                }
        }
    }

    // ---- CREATE: POST /api/v1/platos ----
    /**
     * Registra el plato en el servidor.
     * Retorna true si pasó las validaciones y la petición se envió.
     * El formulario se limpia cuando el servidor confirma el alta (platosRegistrados aumenta).
     */
    fun agregarPlato(nombre: String, descripcion: String, precioTexto: String, categoriaId: Int?): Boolean {
        ValidadorPlato.errorNombre(nombre)?.let { mensajeError.value = it; return false }
        val precio = ValidadorPlato.precioACentimos(precioTexto)
        if (precio.isFailure) { mensajeError.value = precio.exceptionOrNull()?.message; return false }
        if (categoriaId == null) { mensajeError.value = "Seleccione una categoría."; return false }
        mensajeError.value = null

        // El servidor solo acepta altas de un administrador con sesión iniciada.
        if (!repositorio.haySesion) {
            gestion.update { it.copy(mostrarLogin = true, errorLogin = null) }
            return false
        }
        if (operacion.value is EstadoOperacion.Cargando) return false

        val plato = Plato(
            categoriaId = categoriaId,
            nombre = nombre.trim(),
            descripcion = descripcion.trim().ifBlank { "Sin descripción" },
            ingredientes = "Por definir",
            alergenos = "Por confirmar",
            porcion = "Personal",
            precioCentimos = precio.getOrThrow()
        )
        ejecutar("Guardando plato...") {
            repositorio.registrarPlatoEnServidor(plato).fold(
                onSuccess = { creado ->
                    gestion.update { it.copy(platosRegistrados = it.platosRegistrados + 1) }
                    EstadoOperacion.Exito("Plato «${creado.nombre}» registrado en el servidor.")
                },
                onFailure = { error -> falla(error, "registrar el plato") }
            )
        }
        return true
    }

    // ---- UPDATE: PUT /api/v1/platos/{id} ----
    /** Retorna el mensaje de error de validación, o null si la petición se envió. */
    fun editarPlato(plato: Plato, nombre: String, precioTexto: String): String? {
        ValidadorPlato.errorNombre(nombre)?.let { return it }
        val precio = ValidadorPlato.precioACentimos(precioTexto)
        if (precio.isFailure) return precio.exceptionOrNull()?.message
        if (!repositorio.haySesion) {
            gestion.update { it.copy(mostrarLogin = true, errorLogin = null) }
            return "Inicie sesión como administrador para guardar los cambios."
        }
        if (operacion.value is EstadoOperacion.Cargando) return "Espere a que termine la operación en curso."

        val editado = plato.copy(nombre = nombre.trim(), precioCentimos = precio.getOrThrow())
        ejecutar("Guardando cambios...") {
            repositorio.actualizarPlatoEnServidor(editado).fold(
                onSuccess = { actualizado -> EstadoOperacion.Exito("Plato «${actualizado.nombre}» actualizado en el servidor.") },
                onFailure = { error -> falla(error, "guardar los cambios") }
            )
        }
        return null
    }

    /** "Marcar agotado" sigue siendo local: el PATCH de disponibilidad no forma parte de la Actividad 09. */
    fun alternarDisponibilidad(plato: Plato) {
        viewModelScope.launch { repositorio.actualizarPlato(plato.copy(agotado = !plato.agotado)) }
    }

    // ---- DELETE: DELETE /api/v1/platos/{id} ----
    fun darDeBaja(plato: Plato) {
        if (!repositorio.haySesion) {
            gestion.update { it.copy(mostrarLogin = true, errorLogin = null) }
            return
        }
        ejecutar("Dando de baja el plato...") {
            repositorio.darDeBajaEnServidor(plato.id).fold(
                onSuccess = { EstadoOperacion.Exito("Plato «${plato.nombre}» dado de baja en el servidor.") },
                onFailure = { error -> falla(error, "dar de baja el plato") }
            )
        }
    }
}