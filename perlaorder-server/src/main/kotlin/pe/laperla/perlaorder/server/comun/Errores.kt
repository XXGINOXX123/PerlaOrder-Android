package pe.laperla.perlaorder.server.comun

import kotlinx.serialization.Serializable

/** Cuerpo uniforme de error: la app decide por [codigo] y muestra [mensaje]. */
@Serializable
data class ApiError(
    val codigo: String,
    val mensaje: String,
    val detalles: Map<String, String>? = null,
)

/**
 * Jerarquía cerrada de errores de dominio. Los repositorios y servicios lanzan estas
 * excepciones sin conocer HTTP; la traducción a códigos de estado ocurre en un único
 * lugar (plugins/ManejoDeErrores.kt).
 */
sealed class ErrorDeDominio(message: String) : RuntimeException(message)

class ValidacionException(val errores: Map<String, String>) :
    ErrorDeDominio(errores.values.firstOrNull() ?: "Datos inválidos.") {
    constructor(campo: String, mensaje: String) : this(mapOf(campo to mensaje))
}

class NoEncontradoException(mensaje: String) : ErrorDeDominio(mensaje)

class ConflictoException(
    val codigo: String,
    mensaje: String,
    val detalles: Map<String, String>? = null,
) : ErrorDeDominio(mensaje)

class NoAutenticadoException : ErrorDeDominio("Se requiere iniciar sesión.")

class ProhibidoException : ErrorDeDominio("Tu rol no permite realizar esta operación.")

class CuentaBloqueadaException(val segundosRestantes: Long) :
    ErrorDeDominio("Demasiados intentos fallidos. Vuelve a intentarlo en unos minutos.")

/** Acumula errores por campo y lanza una sola excepción con todos. */
class Validador {
    private val errores = linkedMapOf<String, String>()

    fun exigir(condicion: Boolean, campo: String, mensaje: () -> String) {
        if (!condicion) errores.putIfAbsent(campo, mensaje())
    }

    fun lanzarSiHayErrores() {
        if (errores.isNotEmpty()) throw ValidacionException(errores.toMap())
    }
}

inline fun validar(bloque: Validador.() -> Unit) {
    Validador().apply(bloque).lanzarSiHayErrores()
}
