package com.ejemplo.perlaorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.perlaorder.data.local.entity.*
import com.ejemplo.perlaorder.repository.MenuRepository
import com.ejemplo.perlaorder.util.ValidadorPlato
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MenuUiState(
    val categorias: List<Categoria> = emptyList(),
    val platos: List<Plato> = emptyList(),
    val categoriaSeleccionada: Int? = null,   // null = "Todas"
    val mensajeError: String? = null,
    val mensajeInfo: String? = null
)

class MenuViewModel(private val repositorio: MenuRepository) : ViewModel() {

    private val categoriaSeleccionada = MutableStateFlow<Int?>(null)
    private val mensajeError = MutableStateFlow<String?>(null)
    private val mensajeInfo = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MenuUiState> = combine(
        repositorio.categorias, repositorio.platosActivos,
        categoriaSeleccionada, mensajeError, mensajeInfo
    ) { categorias, platos, seleccion, error, info ->
        MenuUiState(
            categorias = categorias,
            platos = if (seleccion == null) platos else platos.filter { it.categoriaId == seleccion },
            categoriaSeleccionada = seleccion,
            mensajeError = error,
            mensajeInfo = info
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuUiState())

    init { viewModelScope.launch { repositorio.prepararCarta() } }

    fun seleccionarCategoria(id: Int?) { categoriaSeleccionada.value = id }
    fun limpiarMensaje() { mensajeInfo.value = null }

    fun actualizarCarta() {
        viewModelScope.launch {
            mensajeInfo.value = if (repositorio.sincronizarCarta())
                "Carta actualizada desde el servidor del restaurante."
            else
                "Sin conexión con el servidor del restaurante. Se muestra la última carta guardada en el dispositivo."
        }
    }

    /** Retorna true si pasó las validaciones (la UI limpia el formulario). */
    fun agregarPlato(nombre: String, descripcion: String, precioTexto: String, categoriaId: Int?): Boolean {
        ValidadorPlato.errorNombre(nombre)?.let { mensajeError.value = it; return false }
        val precio = ValidadorPlato.precioACentimos(precioTexto)
        if (precio.isFailure) { mensajeError.value = precio.exceptionOrNull()?.message; return false }
        if (categoriaId == null) { mensajeError.value = "Seleccione una categoría."; return false }

        mensajeError.value = null
        viewModelScope.launch {
            repositorio.agregarPlato(
                Plato(
                    categoriaId = categoriaId,
                    nombre = nombre.trim(),
                    descripcion = descripcion.trim().ifBlank { "Sin descripción" },
                    ingredientes = "Por definir",
                    alergenos = "Por confirmar",
                    porcion = "Personal",
                    precioCentimos = precio.getOrThrow()
                )
            )
        }
        return true
    }

    /** Retorna el mensaje de error de validación, o null si se guardó. */
    fun editarPlato(plato: Plato, nombre: String, precioTexto: String): String? {
        ValidadorPlato.errorNombre(nombre)?.let { return it }
        val precio = ValidadorPlato.precioACentimos(precioTexto)
        if (precio.isFailure) return precio.exceptionOrNull()?.message
        viewModelScope.launch {
            repositorio.actualizarPlato(plato.copy(nombre = nombre.trim(), precioCentimos = precio.getOrThrow()))
        }
        return null
    }

    fun alternarDisponibilidad(plato: Plato) {
        viewModelScope.launch { repositorio.actualizarPlato(plato.copy(agotado = !plato.agotado)) }
    }

    fun darDeBaja(plato: Plato) {
        viewModelScope.launch { repositorio.darDeBaja(plato.id) }
    }
}