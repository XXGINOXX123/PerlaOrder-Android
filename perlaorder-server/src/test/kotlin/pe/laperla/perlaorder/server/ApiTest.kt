package pe.laperla.perlaorder.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import pe.laperla.perlaorder.server.auth.LoginRequest
import pe.laperla.perlaorder.server.auth.LoginResponse
import pe.laperla.perlaorder.server.carta.CartaSyncResponse
import pe.laperla.perlaorder.server.carta.DisponibilidadRequest
import pe.laperla.perlaorder.server.carta.PlatoActualizarRequest
import pe.laperla.perlaorder.server.carta.PlatoCrearRequest
import pe.laperla.perlaorder.server.comun.ApiError
import pe.laperla.perlaorder.server.config.AdminInicial
import pe.laperla.perlaorder.server.config.AppConfig
import pe.laperla.perlaorder.server.config.JwtConfig
import pe.laperla.perlaorder.server.pedidos.PedidoCrearRequest
import pe.laperla.perlaorder.server.pedidos.PedidoDto
import pe.laperla.perlaorder.server.pedidos.PedidoItemRequest
import pe.laperla.perlaorder.server.plugins.JsonApi
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Pruebas de extremo a extremo sobre el servidor real (rutas, seguridad y SQLite) sin
 * abrir un puerto: testApplication ejecuta el módulo en memoria.
 */
class ApiTest {

    @Test
    fun `la carta se sincroniza de forma incremental`() = testApplication {
        application { modulo(configuracionDePrueba()) }
        val cliente = clienteJson()

        val inicial = cliente.get("/api/v1/carta").body<CartaSyncResponse>()
        assertTrue(inicial.completa)
        assertEquals(3, inicial.categorias.size)
        assertEquals(7, inicial.platos.size)

        val token = cliente.iniciarSesion(CLAVE_ADMIN).body<LoginResponse>().token
        val edicion = cliente.put("/api/v1/platos/1") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PlatoActualizarRequest(precioCentimos = 900))
        }
        assertEquals(HttpStatusCode.OK, edicion.status)

        // La tablet solo recibe la fila que cambió.
        val delta = cliente.get("/api/v1/carta?desde=${inicial.revision}").body<CartaSyncResponse>()
        assertFalse(delta.completa)
        assertTrue(delta.revision > inicial.revision)
        assertEquals(listOf(1), delta.platos.map { it.id })
        assertEquals(900L, delta.platos.single().precioCentimos)
        assertTrue(delta.categorias.isEmpty())

        val sinCambios = cliente.get("/api/v1/carta?desde=${delta.revision}").body<CartaSyncResponse>()
        assertTrue(sinCambios.platos.isEmpty())
        assertEquals(delta.revision, sinCambios.revision)
    }

    @Test
    fun `la gestion de la carta exige sesion y valida los datos`() = testApplication {
        application { modulo(configuracionDePrueba()) }
        val cliente = clienteJson()
        val nuevoPlato = PlatoCrearRequest(categoriaId = 1, nombre = "Tequeños", precioCentimos = 1500)

        val sinToken = cliente.post("/api/v1/platos") {
            contentType(ContentType.Application.Json)
            setBody(nuevoPlato)
        }
        assertEquals(HttpStatusCode.Unauthorized, sinToken.status)

        val token = cliente.iniciarSesion(CLAVE_ADMIN).body<LoginResponse>().token
        val precioInvalido = cliente.post("/api/v1/platos") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(nuevoPlato.copy(precioCentimos = 0))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, precioInvalido.status)
        assertEquals("VALIDACION", precioInvalido.body<ApiError>().codigo)

        val creado = cliente.post("/api/v1/platos") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(nuevoPlato)
        }
        assertEquals(HttpStatusCode.Created, creado.status)
    }

    @Test
    fun `tres intentos fallidos bloquean la cuenta durante cinco minutos`() = testApplication {
        val reloj = RelojDePrueba()
        application { modulo(configuracionDePrueba(), reloj) }
        val cliente = clienteJson()

        repeat(3) {
            assertEquals(HttpStatusCode.Unauthorized, cliente.iniciarSesion("clave-equivocada").status)
        }
        // Bloqueada: ni siquiera la contraseña correcta entra.
        assertEquals(HttpStatusCode.TooManyRequests, cliente.iniciarSesion(CLAVE_ADMIN).status)

        reloj.avanzar(5.minutes + 1.seconds)
        assertEquals(HttpStatusCode.OK, cliente.iniciarSesion(CLAVE_ADMIN).status)
    }

    @Test
    fun `un pedido reenviado no se duplica y el total lo calcula el servidor`() = testApplication {
        application { modulo(configuracionDePrueba()) }
        val cliente = clienteJson()
        val pedido = PedidoCrearRequest(
            claveIdempotencia = UUID.randomUUID().toString(),
            mesa = 4,
            items = listOf(
                PedidoItemRequest(platoId = 1, cantidad = 2, indicaciones = "sin cebolla"), // 2 x S/ 8.00
                PedidoItemRequest(platoId = 6, cantidad = 1),                               // 1 x S/ 6.00
            ),
        )

        val primero = cliente.enviarPedido(pedido)
        assertEquals(HttpStatusCode.Created, primero.status)
        val registrado = primero.body<PedidoDto>()
        assertEquals(2200L, registrado.totalCentimos)

        val reenvio = cliente.enviarPedido(pedido)
        assertEquals(HttpStatusCode.OK, reenvio.status)
        assertEquals(registrado.id, reenvio.body<PedidoDto>().id)
    }

    @Test
    fun `no se acepta un pedido con un plato agotado`() = testApplication {
        application { modulo(configuracionDePrueba()) }
        val cliente = clienteJson()
        val token = cliente.iniciarSesion(CLAVE_ADMIN).body<LoginResponse>().token
        cliente.patch("/api/v1/platos/3/disponibilidad") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DisponibilidadRequest(agotado = true))
        }

        val respuesta = cliente.enviarPedido(
            PedidoCrearRequest(UUID.randomUUID().toString(), mesa = 1, items = listOf(PedidoItemRequest(3, 1)))
        )
        assertEquals(HttpStatusCode.Conflict, respuesta.status)
        assertEquals("PLATOS_NO_DISPONIBLES", respuesta.body<ApiError>().codigo)
    }

    // ------------------------------------------------------------------ utilidades

    private fun ApplicationTestBuilder.clienteJson(): HttpClient =
        createClient { install(ContentNegotiation) { json(JsonApi) } }

    private suspend fun HttpClient.iniciarSesion(contrasena: String): HttpResponse =
        post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(usuario = "admin", contrasena = contrasena))
        }

    private suspend fun HttpClient.enviarPedido(pedido: PedidoCrearRequest): HttpResponse =
        post("/api/v1/pedidos") {
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }

    /** Cada prueba usa su propio archivo SQLite temporal: no comparten estado. */
    private fun configuracionDePrueba(): AppConfig {
        val archivo = Files.createTempFile("perlaorder-test-", ".db").toFile().apply { deleteOnExit() }
        return AppConfig(
            host = "127.0.0.1",
            puerto = 0,
            rutaBaseDeDatos = archivo.absolutePath,
            jwt = JwtConfig(secreto = "secreto-solo-para-pruebas-con-mas-de-32-caracteres"),
            adminInicial = AdminInicial(usuario = "admin", contrasena = CLAVE_ADMIN),
            numeroDeMesas = 20,
        )
    }

    private companion object {
        const val CLAVE_ADMIN = "Clave-de-prueba-123"
    }
}

/** Reloj controlable. Parte de la hora real porque el verificador de JWT usa el reloj del sistema. */
private class RelojDePrueba(private var instante: Instant = Instant.now()) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instante
    fun avanzar(duracion: Duration) {
        instante = instante.plusMillis(duracion.inWholeMilliseconds)
    }
}
