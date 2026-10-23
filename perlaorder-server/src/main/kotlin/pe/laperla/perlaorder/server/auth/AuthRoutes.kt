package pe.laperla.perlaorder.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import pe.laperla.perlaorder.server.comun.ApiError
import pe.laperla.perlaorder.server.comun.CuentaBloqueadaException
import pe.laperla.perlaorder.server.db.Rol
import pe.laperla.perlaorder.server.plugins.AUTH_PERSONAL
import pe.laperla.perlaorder.server.plugins.LIMITE_LOGIN
import pe.laperla.perlaorder.server.plugins.usuarioCon

@Serializable
data class PerfilResponse(val id: Int, val usuario: String, val rol: Rol)

fun Route.rutasDeAutenticacion(auth: AuthService) {
    route("/auth") {
        rateLimit(LIMITE_LOGIN) {
            post("/login") {
                when (val resultado = auth.iniciarSesion(call.receive<LoginRequest>())) {
                    is ResultadoLogin.Exito -> call.respond(resultado.sesion)
                    // Mismo mensaje para usuario inexistente y contraseña errónea (CA-05.2).
                    ResultadoLogin.CredencialesInvalidas -> call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError("CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos."),
                    )
                    is ResultadoLogin.Bloqueado -> throw CuentaBloqueadaException(resultado.segundosRestantes)
                }
            }
        }
        authenticate(AUTH_PERSONAL) {
            // La app la usa al abrir para saber si el token guardado sigue vigente.
            get("/yo") {
                val usuario = call.usuarioCon()
                call.respond(PerfilResponse(usuario.id, usuario.usuario, usuario.rol))
            }
        }
    }
}
