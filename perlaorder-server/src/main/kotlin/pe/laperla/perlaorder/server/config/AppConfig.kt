package pe.laperla.perlaorder.server.config

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Configuración inmutable del servidor. Se construye una sola vez en el arranque y se
 * inyecta por parámetro: ninguna clase lee variables de entorno por su cuenta, de modo
 * que las pruebas pueden levantar el servidor con valores propios.
 */
data class AppConfig(
    val host: String,
    val puerto: Int,
    val rutaBaseDeDatos: String,
    val jwt: JwtConfig,
    val adminInicial: AdminInicial,
    val numeroDeMesas: Int,
) {
    companion object {
        private val log = LoggerFactory.getLogger(AppConfig::class.java)
        private const val LONGITUD_MINIMA_SECRETO = 32

        fun desdeEntorno(env: Map<String, String> = System.getenv()): AppConfig {
            fun valor(clave: String): String? = env[clave]?.trim()?.takeIf { it.isNotEmpty() }

            val secreto = valor("PERLA_JWT_SECRET")?.also {
                require(it.length >= LONGITUD_MINIMA_SECRETO) {
                    "PERLA_JWT_SECRET debe tener al menos $LONGITUD_MINIMA_SECRETO caracteres."
                }
            } ?: secretoAleatorio().also {
                log.warn(
                    "PERLA_JWT_SECRET no está definido: se usa un secreto aleatorio. " +
                        "Las sesiones del personal se invalidarán al reiniciar el servidor."
                )
            }

            return AppConfig(
                host = valor("PERLA_HOST") ?: "0.0.0.0",
                puerto = valor("PERLA_PORT")?.toIntOrNull() ?: 8080,
                rutaBaseDeDatos = valor("PERLA_DB_PATH") ?: "data/perlaorder.db",
                jwt = JwtConfig(secreto = secreto),
                adminInicial = AdminInicial(
                    usuario = valor("PERLA_ADMIN_USER") ?: "admin",
                    contrasena = valor("PERLA_ADMIN_PASSWORD"),
                ),
                numeroDeMesas = valor("PERLA_MESAS")?.toIntOrNull()?.takeIf { it > 0 } ?: 20,
            )
        }

        private fun secretoAleatorio(): String {
            val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
            return Base64.getEncoder().encodeToString(bytes)
        }
    }
}

data class JwtConfig(
    val secreto: String,
    val emisor: String = "perlaorder-server",
    val audiencia: String = "perlaorder-app",
    /** Un turno de trabajo: el personal inicia sesión una vez por jornada. */
    val vigencia: Duration = 8.hours,
) {
    // Evita que el secreto termine en un log por un toString() accidental.
    override fun toString(): String = "JwtConfig(emisor=$emisor, audiencia=$audiencia, vigencia=$vigencia)"
}

data class AdminInicial(val usuario: String, val contrasena: String?) {
    override fun toString(): String = "AdminInicial(usuario=$usuario)"
}
