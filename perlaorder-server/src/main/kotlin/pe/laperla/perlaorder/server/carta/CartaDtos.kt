package pe.laperla.perlaorder.server.carta

import kotlinx.serialization.Serializable

@Serializable
data class CategoriaDto(val id: Int, val nombre: String, val orden: Int)

@Serializable
data class PlatoDto(
    val id: Int,
    val categoriaId: Int,
    val nombre: String,
    val descripcion: String,
    val ingredientes: String,
    val alergenos: String,
    val porcion: String,
    val precioCentimos: Long,
    val imagenUrl: String?,
    val agotado: Boolean,
    val activo: Boolean,
    val revision: Long,
)

/**
 * Respuesta de GET /api/v1/carta?desde={revision}.
 *
 * - [completa] = true: la respuesta trae toda la carta y la tablet debe tomarla como
 *   verdad absoluta (primera carga o cambio de servidor).
 * - [completa] = false: solo trae las filas modificadas después de `desde`.
 * La tablet guarda [revision] y [servidorId] y los envía en la siguiente consulta.
 */
@Serializable
data class CartaSyncResponse(
    val servidorId: String,
    val revision: Long,
    val generadoEn: Long,
    val completa: Boolean,
    val categorias: List<CategoriaDto>,
    val platos: List<PlatoDto>,
)

@Serializable
data class PlatoCrearRequest(
    val categoriaId: Int,
    val nombre: String,
    val precioCentimos: Long,
    val descripcion: String = "",
    val ingredientes: String = "",
    val alergenos: String = "",
    val porcion: String = "",
    val imagenUrl: String? = null,
)

/** Actualización parcial: un campo ausente (null) significa "no cambiar". */
@Serializable
data class PlatoActualizarRequest(
    val categoriaId: Int? = null,
    val nombre: String? = null,
    val precioCentimos: Long? = null,
    val descripcion: String? = null,
    val ingredientes: String? = null,
    val alergenos: String? = null,
    val porcion: String? = null,
)

@Serializable
data class DisponibilidadRequest(val agotado: Boolean)

@Serializable
data class CambioDto(
    val campo: String,
    val valorAnterior: String?,
    val valorNuevo: String,
    val usuario: String,
    val fecha: Long,
)
