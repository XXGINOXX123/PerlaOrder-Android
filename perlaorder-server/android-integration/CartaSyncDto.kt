// Destino sugerido en la app: data/network/dto/CartaSyncDto.kt
// PLANTILLA: ajusta el `package` y los nombres de propiedades de tus entidades Room.
package pe.laperla.perlaorder.data.network.dto

/** Espejo de CartaSyncResponse del servidor. Gson lo llena por nombre de campo. */
data class CartaSyncDto(
    val servidorId: String,
    val revision: Long,
    val generadoEn: Long,
    val completa: Boolean,
    val categorias: List<CategoriaDto>,
    val platos: List<PlatoDto>,
)

data class CategoriaDto(val id: Int, val nombre: String, val orden: Int)

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

// Los DTO no salen de la capa de datos: el repositorio los convierte a entidades Room y
// el ViewModel nunca ve un tipo de red. Así un cambio en la API no llega a la interfaz.
//
// fun CategoriaDto.aEntidad() = Categoria(id = id, nombre = nombre, orden = orden)
//
// fun PlatoDto.aEntidad() = Plato(
//     id = id, categoriaId = categoriaId, nombre = nombre, descripcion = descripcion,
//     ingredientes = ingredientes, alergenos = alergenos, porcion = porcion,
//     precioCentimos = precioCentimos, imagenUrl = imagenUrl, agotado = agotado, activo = activo,
// )
