package pe.laperla.perlaorder.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import pe.laperla.perlaorder.server.config.JwtConfig
import pe.laperla.perlaorder.server.db.BaseDeDatos
import pe.laperla.perlaorder.server.db.Rol
import pe.laperla.perlaorder.server.db.Usuarios
import java.time.Clock
import java.util.Date
import kotlin.time.Duration.Companion.minutes

/** Factor de trabajo de BCrypt: ~250 ms por verificación en un equipo de escritorio. */
const val COSTO_BCRYPT = 12

@Serializable
data class LoginRequest(val usuario: String, val contrasena: String)

@Serializable
data class LoginResponse(val token: String, val expiraEn: Long, val usuario: String, val rol: Rol)

sealed interface ResultadoLogin {
    data class Exito(val sesion: LoginResponse) : ResultadoLogin

    /** Usuario inexistente, inactivo o contraseña errónea: la respuesta no distingue cuál (CA-05.2). */
    data object CredencialesInvalidas : ResultadoLogin

    data class Bloqueado(val segundosRestantes: Long) : ResultadoLogin
}

class AuthService(
    private val bd: BaseDeDatos,
    private val jwt: JwtConfig,
    private val reloj: Clock,
) {
    private val algoritmo = Algorithm.HMAC256(jwt.secreto)

    // Hash de relleno: cuando el usuario no existe igual se ejecuta una verificación BCrypt,
    // para que el tiempo de respuesta no revele qué usuarios están registrados.
    private val hashDeRelleno: String by lazy {
        BCrypt.withDefaults().hashToString(COSTO_BCRYPT, "relleno-sin-uso".toCharArray())
    }

    private data class Cuenta(val id: Int, val usuario: String, val hash: String, val rol: Rol, val bloqueadoHasta: Long?)

    suspend fun iniciarSesion(solicitud: LoginRequest): ResultadoLogin {
        val nombre = solicitud.usuario.trim().lowercase()
        // BCrypt solo procesa 72 bytes; se rechaza antes de llegar a la librería.
        val entradaValida = nombre.length in 1..40 &&
            solicitud.contrasena.isNotEmpty() &&
            solicitud.contrasena.toByteArray().size <= 72

        val cuenta = if (entradaValida) buscarCuentaActiva(nombre) else null

        val ahora = reloj.millis()
        val bloqueadoHasta = cuenta?.bloqueadoHasta
        if (bloqueadoHasta != null && bloqueadoHasta > ahora) {
            return ResultadoLogin.Bloqueado(segundosRestantes = (bloqueadoHasta - ahora + 999) / 1000)
        }

        // La verificación es costosa en CPU a propósito: fuera de la transacción y del hilo de E/S.
        val coincide = withContext(Dispatchers.Default) {
            val contrasena = if (entradaValida) solicitud.contrasena else "x"
            BCrypt.verifyer().verify(contrasena.toCharArray(), cuenta?.hash ?: hashDeRelleno).verified
        }

        if (cuenta == null) return ResultadoLogin.CredencialesInvalidas
        if (!coincide) {
            registrarFallo(cuenta.id, ahora)
            return ResultadoLogin.CredencialesInvalidas
        }

        reiniciarIntentos(cuenta.id)
        return ResultadoLogin.Exito(emitirSesion(cuenta, ahora))
    }

    private suspend fun buscarCuentaActiva(nombre: String): Cuenta? = bd.leer {
        Usuarios.selectAll()
            .where { Usuarios.usuario eq nombre }
            .singleOrNull()
            ?.takeIf { it[Usuarios.activo] }
            ?.let {
                Cuenta(
                    id = it[Usuarios.id],
                    usuario = it[Usuarios.usuario],
                    hash = it[Usuarios.hashContrasena],
                    rol = it[Usuarios.rol],
                    bloqueadoHasta = it[Usuarios.bloqueadoHasta],
                )
            }
    }

    /** RN-07: al tercer fallo consecutivo la cuenta queda bloqueada cinco minutos. */
    private suspend fun registrarFallo(usuarioId: Int, ahora: Long) = bd.escribir {
        // Incremento en SQL (no "leer, sumar, guardar") para que sea atómico.
        Usuarios.update({ Usuarios.id eq usuarioId }) { it[intentosFallidos] = intentosFallidos + 1 }
        val intentos = Usuarios.selectAll().where { Usuarios.id eq usuarioId }.single()[Usuarios.intentosFallidos]
        if (intentos >= MAX_INTENTOS) {
            Usuarios.update({ Usuarios.id eq usuarioId }) {
                it[intentosFallidos] = 0
                it[bloqueadoHasta] = ahora + DURACION_BLOQUEO.inWholeMilliseconds
            }
        }
    }

    private suspend fun reiniciarIntentos(usuarioId: Int) = bd.escribir {
        Usuarios.update({ Usuarios.id eq usuarioId }) {
            it[intentosFallidos] = 0
            it[bloqueadoHasta] = null
        }
    }

    private fun emitirSesion(cuenta: Cuenta, ahora: Long): LoginResponse {
        val expiraEn = ahora + jwt.vigencia.inWholeMilliseconds
        val token = JWT.create()
            .withIssuer(jwt.emisor)
            .withAudience(jwt.audiencia)
            .withSubject(cuenta.id.toString())
            .withClaim(CLAIM_USUARIO, cuenta.usuario)
            .withClaim(CLAIM_ROL, cuenta.rol.name)
            .withIssuedAt(Date(ahora))
            .withExpiresAt(Date(expiraEn))
            .sign(algoritmo)
        return LoginResponse(token = token, expiraEn = expiraEn, usuario = cuenta.usuario, rol = cuenta.rol)
    }

    companion object {
        const val CLAIM_USUARIO = "usr"
        const val CLAIM_ROL = "rol"
        const val MAX_INTENTOS = 3
        val DURACION_BLOQUEO = 5.minutes
    }
}
