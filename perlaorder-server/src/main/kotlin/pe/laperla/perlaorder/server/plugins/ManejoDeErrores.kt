package pe.laperla.perlaorder.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import pe.laperla.perlaorder.server.comun.ApiError
import pe.laperla.perlaorder.server.comun.ConflictoException
import pe.laperla.perlaorder.server.comun.CuentaBloqueadaException
import pe.laperla.perlaorder.server.comun.NoAutenticadoException
import pe.laperla.perlaorder.server.comun.NoEncontradoException
import pe.laperla.perlaorder.server.comun.ProhibidoException
import pe.laperla.perlaorder.server.comun.ValidacionException

private val log = LoggerFactory.getLogger("pe.laperla.perlaorder.server.Errores")

/**
 * Único lugar donde un error de dominio se convierte en respuesta HTTP. Las rutas no
 * llevan try/catch y todas las respuestas de error comparten el formato [ApiError].
 */
fun Application.configurarManejoDeErrores() {
    install(StatusPages) {
        exception<ValidacionException> { call, causa ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiError("VALIDACION", causa.message ?: "Datos inválidos.", causa.errores),
            )
        }
        exception<NoEncontradoException> { call, causa ->
            call.respond(HttpStatusCode.NotFound, ApiError("NO_ENCONTRADO", causa.message ?: "Recurso no encontrado."))
        }
        exception<ConflictoException> { call, causa ->
            call.respond(HttpStatusCode.Conflict, ApiError(causa.codigo, causa.message ?: "Conflicto.", causa.detalles))
        }
        exception<NoAutenticadoException> { call, causa ->
            call.respond(HttpStatusCode.Unauthorized, ApiError("NO_AUTENTICADO", causa.message ?: "No autenticado."))
        }
        exception<ProhibidoException> { call, causa ->
            call.respond(HttpStatusCode.Forbidden, ApiError("PROHIBIDO", causa.message ?: "Operación no permitida."))
        }
        exception<CuentaBloqueadaException> { call, causa ->
            call.response.header(HttpHeaders.RetryAfter, causa.segundosRestantes.toString())
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError("CUENTA_BLOQUEADA", causa.message ?: "Cuenta bloqueada temporalmente."),
            )
        }
        // JSON mal formado, campo obligatorio ausente o tipo incorrecto en el cuerpo.
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError("SOLICITUD_INVALIDA", "La solicitud no tiene el formato esperado."),
            )
        }
        // Cualquier otro fallo: el detalle queda en el log del servidor y el cliente recibe
        // un mensaje genérico. Una traza de error nunca debe salir por la red.
        exception<Throwable> { call, causa ->
            log.error("Error no controlado en {} {}", call.request.local.method.value, call.request.local.uri, causa)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("ERROR_INTERNO", "Ocurrió un error inesperado en el servidor."),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ApiError("NO_ENCONTRADO", "La ruta solicitada no existe."))
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError("DEMASIADAS_SOLICITUDES", "Demasiados intentos. Espera un momento y vuelve a intentar."),
            )
        }
    }
}
