package pe.laperla.perlaorder.server.carta

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import pe.laperla.perlaorder.server.comun.ValidacionException
import pe.laperla.perlaorder.server.db.Rol
import pe.laperla.perlaorder.server.plugins.AUTH_PERSONAL
import pe.laperla.perlaorder.server.plugins.usuarioCon

/**
 * GET /api/platos — catálogo completo de platos (Actividad 08).
 * Pública y sin parámetros. Se monta en /api, fuera del prefijo versionado /api/v1.
 */
fun Route.rutaCatalogoDePlatos(carta: CartaRepository) {
    get("/platos") {
        call.respond(carta.listarPlatos())
    }
}

fun Route.rutasDeCarta(carta: CartaRepository) {
    // Pública: la consulta de la carta no exige sesión (el comensal no se registra, RNF-10).
    get("/carta") {
        val desde = call.request.queryParameters["desde"]?.let {
            it.toLongOrNull() ?: throw ValidacionException("desde", "El parámetro 'desde' debe ser un número entero.")
        } ?: 0L
        call.respond(carta.cambiosDesde(desde))
    }

    authenticate(AUTH_PERSONAL) {
        route("/platos") {
            // RN-04: alta, edición de precio y baja son exclusivas del administrador.
            post {
                val usuario = call.usuarioCon(Rol.ADMIN)
                val creado = carta.crear(call.receive<PlatoCrearRequest>(), usuario.id)
                call.respond(HttpStatusCode.Created, creado)
            }
            put("/{id}") {
                val usuario = call.usuarioCon(Rol.ADMIN)
                call.respond(carta.actualizar(call.idDePlato(), call.receive<PlatoActualizarRequest>(), usuario.id))
            }
            delete("/{id}") {
                val usuario = call.usuarioCon(Rol.ADMIN)
                call.respond(carta.darDeBaja(call.idDePlato(), usuario.id))
            }
            get("/{id}/historial") {
                call.usuarioCon(Rol.ADMIN)
                call.respond(carta.historial(call.idDePlato()))
            }
            // El colaborador solo puede cambiar la disponibilidad (CA-05.5).
            patch("/{id}/disponibilidad") {
                val usuario = call.usuarioCon(Rol.ADMIN, Rol.COLABORADOR)
                val solicitud = call.receive<DisponibilidadRequest>()
                call.respond(carta.cambiarDisponibilidad(call.idDePlato(), solicitud.agotado, usuario.id))
            }
        }
    }
}

private fun ApplicationCall.idDePlato(): Int =
    parameters["id"]?.toIntOrNull() ?: throw ValidacionException("id", "El identificador del plato no es válido.")
