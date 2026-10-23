package pe.laperla.perlaorder.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import pe.laperla.perlaorder.server.auth.AuthService
import pe.laperla.perlaorder.server.auth.rutasDeAutenticacion
import pe.laperla.perlaorder.server.carta.CartaRepository
import pe.laperla.perlaorder.server.carta.rutaCatalogoDePlatos
import pe.laperla.perlaorder.server.carta.rutasDeCarta
import pe.laperla.perlaorder.server.config.AppConfig
import pe.laperla.perlaorder.server.db.BaseDeDatos
import pe.laperla.perlaorder.server.db.Semilla
import pe.laperla.perlaorder.server.pedidos.PedidoRepository
import pe.laperla.perlaorder.server.pedidos.rutasDePedidos
import pe.laperla.perlaorder.server.plugins.configurarManejoDeErrores
import pe.laperla.perlaorder.server.plugins.configurarSeguridad
import pe.laperla.perlaorder.server.plugins.configurarSerializacion
import java.time.Clock

@Serializable
data class EstadoServidor(val estado: String, val horaServidor: Long)

fun main() {
    val config = AppConfig.desdeEntorno()
    // Variable con tipo explícito: embeddedServer tiene una variante suspendida y otra no.
    val modulo: Application.() -> Unit = { modulo(config) }
    embeddedServer(Netty, port = config.puerto, host = config.host, module = modulo).start(wait = true)
}

/**
 * Raíz de composición del servidor, equivalente a PerlaOrderApp en la app: aquí se crean
 * las dependencias y se entregan por constructor. Sin contenedor de inyección: con este
 * tamaño, el cableado manual se lee de arriba abajo y falla en compilación, no en ejecución.
 *
 * [reloj] se inyecta para que las pruebas controlen el tiempo (bloqueo de cinco minutos).
 */
fun Application.modulo(config: AppConfig, reloj: Clock = Clock.systemUTC()) {
    val bd = BaseDeDatos.conectar(config.rutaBaseDeDatos)
    // Única espera bloqueante, y ocurre antes de aceptar tráfico.
    runBlocking { Semilla.aplicar(bd, config.adminInicial, reloj) }

    val carta = CartaRepository(bd, reloj)
    val pedidos = PedidoRepository(bd, reloj, config.numeroDeMesas)
    val auth = AuthService(bd, config.jwt, reloj)

    install(CallLogging)
    configurarSerializacion()
    configurarManejoDeErrores()
    configurarSeguridad(config.jwt)

    routing {
        // Sin autenticación: la app lo usa para saber si el servidor del local responde.
        get("/health") {
            call.respond(EstadoServidor(estado = "ok", horaServidor = reloj.millis()))
        }
        // Actividad 08: catálogo de platos para el cliente Retrofit de la app (GET /api/platos).
        route("/api") {
            rutaCatalogoDePlatos(carta)
        }
        // Prefijo versionado: un cambio incompatible irá en /api/v2 sin romper tablets
        // que aún no se actualizaron.
        route("/api/v1") {
            rutasDeAutenticacion(auth)
            rutasDeCarta(carta)
            rutasDePedidos(pedidos)
        }
    }
}
