package pe.laperla.perlaorder.server.pedidos

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import pe.laperla.perlaorder.server.comun.ValidacionException
import pe.laperla.perlaorder.server.db.EstadoPedido
import pe.laperla.perlaorder.server.db.Rol
import pe.laperla.perlaorder.server.plugins.AUTH_PERSONAL
import pe.laperla.perlaorder.server.plugins.usuarioCon

fun Route.rutasDePedidos(pedidos: PedidoRepository) {
    route("/pedidos") {
        // Pública: la envía la tablet de la mesa, sin datos del comensal (RNF-10).
        post {
            val resultado = pedidos.registrar(call.receive<PedidoCrearRequest>())
            // 201 si se creó; 200 si era el reenvío de un pedido ya registrado.
            call.respond(if (resultado.creado) HttpStatusCode.Created else HttpStatusCode.OK, resultado.pedido)
        }

        authenticate(AUTH_PERSONAL) {
            get {
                call.usuarioCon(Rol.ADMIN, Rol.COLABORADOR)
                val estado = call.request.queryParameters["estado"]?.let { texto ->
                    EstadoPedido.entries.firstOrNull { it.name == texto.uppercase() }
                        ?: throw ValidacionException("estado", "Estado de pedido desconocido: $texto.")
                }
                call.respond(pedidos.listar(estado))
            }
            patch("/{id}/estado") {
                call.usuarioCon(Rol.ADMIN, Rol.COLABORADOR)
                val pedidoId = call.parameters["id"]?.toLongOrNull()
                    ?: throw ValidacionException("id", "El identificador del pedido no es válido.")
                call.respond(pedidos.cambiarEstado(pedidoId, call.receive<EstadoPedidoRequest>().estado))
            }
        }
    }
}
