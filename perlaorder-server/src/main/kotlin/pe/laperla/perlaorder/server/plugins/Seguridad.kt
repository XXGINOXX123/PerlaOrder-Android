package pe.laperla.perlaorder.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respond
import pe.laperla.perlaorder.server.auth.AuthService
import pe.laperla.perlaorder.server.comun.ApiError
import pe.laperla.perlaorder.server.comun.NoAutenticadoException
import pe.laperla.perlaorder.server.comun.ProhibidoException
import pe.laperla.perlaorder.server.config.JwtConfig
import pe.laperla.perlaorder.server.db.Rol
import kotlin.time.Duration.Companion.minutes

const val AUTH_PERSONAL = "personal-jwt"
val LIMITE_LOGIN = RateLimitName("login")

/** Identidad extraída del token ya verificado. Es lo único que ven las rutas. */
data class UsuarioAutenticado(val id: Int, val usuario: String, val rol: Rol)

fun Application.configurarSeguridad(jwt: JwtConfig) {
    install(Authentication) {
        jwt(AUTH_PERSONAL) {
            realm = "perlaorder"
            verifier(
                JWT.require(Algorithm.HMAC256(jwt.secreto))
                    .withIssuer(jwt.emisor)
                    .withAudience(jwt.audiencia)
                    .build()
            )
            // La firma y la expiración ya fueron comprobadas por el verificador; aquí solo
            // se exige que el token traiga los datos que las rutas necesitan.
            validate { credencial ->
                val id = credencial.payload.subject?.toIntOrNull()
                val rol = credencial.payload.getClaim(AuthService.CLAIM_ROL).asString()
                    ?.let { texto -> Rol.entries.firstOrNull { it.name == texto } }
                val usuario = credencial.payload.getClaim(AuthService.CLAIM_USUARIO).asString()
                if (id != null && rol != null && usuario != null) UsuarioAutenticado(id, usuario, rol) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError("NO_AUTENTICADO", "Sesión inválida o vencida. Inicia sesión nuevamente."),
                )
            }
        }
    }

    // Segunda barrera contra fuerza bruta, por dirección IP. El bloqueo por cuenta (RN-07)
    // protege a un usuario concreto; este límite frena a quien prueba muchos usuarios distintos.
    install(RateLimit) {
        register(LIMITE_LOGIN) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { llamada -> llamada.request.local.remoteAddress }
        }
    }
}

/**
 * Devuelve el usuario autenticado y exige que tenga alguno de los [rolesPermitidos].
 * La autorización vive en el servidor: ocultar un botón en la app no es control de acceso.
 */
fun ApplicationCall.usuarioCon(vararg rolesPermitidos: Rol): UsuarioAutenticado {
    val usuario = principal<UsuarioAutenticado>() ?: throw NoAutenticadoException()
    if (rolesPermitidos.isNotEmpty() && usuario.rol !in rolesPermitidos) throw ProhibidoException()
    return usuario
}
