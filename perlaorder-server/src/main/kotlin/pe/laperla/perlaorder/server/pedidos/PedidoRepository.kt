package pe.laperla.perlaorder.server.pedidos

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import pe.laperla.perlaorder.server.comun.ConflictoException
import pe.laperla.perlaorder.server.comun.NoEncontradoException
import pe.laperla.perlaorder.server.comun.validar
import pe.laperla.perlaorder.server.db.BaseDeDatos
import pe.laperla.perlaorder.server.db.EstadoPedido
import pe.laperla.perlaorder.server.db.PedidoItems
import pe.laperla.perlaorder.server.db.Pedidos
import pe.laperla.perlaorder.server.db.Platos
import java.time.Clock
import java.util.UUID

@Serializable
data class PedidoItemRequest(val platoId: Int, val cantidad: Int, val indicaciones: String = "")

/**
 * La tablet envía únicamente identificadores y cantidades. Nombres, precios y total los
 * determina el servidor: un cliente alterado no puede fijar su propio precio.
 */
@Serializable
data class PedidoCrearRequest(
    val claveIdempotencia: String,
    val mesa: Int,
    val items: List<PedidoItemRequest>,
)

@Serializable
data class PedidoItemDto(
    val platoId: Int,
    val nombrePlato: String,
    val precioUnitarioCentimos: Long,
    val cantidad: Int,
    val indicaciones: String,
    val subtotalCentimos: Long,
)

@Serializable
data class PedidoDto(
    val id: Long,
    val mesa: Int,
    val estado: EstadoPedido,
    val totalCentimos: Long,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val items: List<PedidoItemDto>,
)

@Serializable
data class EstadoPedidoRequest(val estado: EstadoPedido)

/** [creado] = false indica que la clave ya existía y se devuelve el pedido original. */
data class PedidoRegistrado(val pedido: PedidoDto, val creado: Boolean)

class PedidoRepository(
    private val bd: BaseDeDatos,
    private val reloj: Clock,
    private val numeroDeMesas: Int,
) {

    /**
     * Registra el pedido de una mesa de forma idempotente.
     *
     * Con Wi-Fi inestable es normal que la tablet no reciba la respuesta y reintente. La
     * clave generada en la tablet (un UUID por cada intento de «Confirmar pedido»)
     * permite reconocer el reenvío y devolver el pedido ya creado en lugar de mandar dos
     * comandas a cocina.
     */
    suspend fun registrar(solicitud: PedidoCrearRequest): PedidoRegistrado {
        validar {
            exigir(esUuid(solicitud.claveIdempotencia), "claveIdempotencia") { "La clave del pedido no es válida." }
            exigir(solicitud.mesa in 1..numeroDeMesas, "mesa") { "El número de mesa debe estar entre 1 y $numeroDeMesas." }
            exigir(solicitud.items.isNotEmpty(), "items") { "El pedido no tiene platos." }
            exigir(solicitud.items.size <= MAX_LINEAS, "items") { "El pedido supera las $MAX_LINEAS líneas." }
            exigir(solicitud.items.all { it.cantidad in 1..MAX_CANTIDAD }, "items") {
                "La cantidad de cada plato debe estar entre 1 y $MAX_CANTIDAD."
            }
            exigir(solicitud.items.all { it.indicaciones.length <= 200 }, "items") {
                "Las indicaciones no pueden superar los 200 caracteres."
            }
        }

        return bd.escribir {
            Pedidos.selectAll()
                .where { Pedidos.claveIdempotencia eq solicitud.claveIdempotencia }
                .singleOrNull()
                ?.let { return@escribir PedidoRegistrado(aPedidoDto(it), creado = false) }

            val idsSolicitados = solicitud.items.map { it.platoId }.distinct()
            val platos = Platos.selectAll()
                .where { Platos.id inList idsSolicitados }
                .associateBy { it[Platos.id] }

            // La carta de la tablet puede estar desactualizada: se revalida contra el estado real.
            val noDisponibles = idsSolicitados.filter { id ->
                val plato = platos[id]
                plato == null || !plato[Platos.activo] || plato[Platos.agotado]
            }
            if (noDisponibles.isNotEmpty()) {
                throw ConflictoException(
                    codigo = "PLATOS_NO_DISPONIBLES",
                    mensaje = "Algunos platos ya no están disponibles. Actualiza la carta y revisa tu pedido.",
                    detalles = mapOf("platoIds" to noDisponibles.joinToString(",")),
                )
            }

            val total = solicitud.items.sumOf { platos.getValue(it.platoId)[Platos.precioCentimos] * it.cantidad }
            val ahora = reloj.millis()
            val nuevoId = Pedidos.insert {
                it[claveIdempotencia] = solicitud.claveIdempotencia
                it[mesa] = solicitud.mesa
                it[estado] = EstadoPedido.PENDIENTE
                it[totalCentimos] = total
                it[creadoEn] = ahora
                it[actualizadoEn] = ahora
            } get Pedidos.id

            PedidoItems.batchInsert(solicitud.items, shouldReturnGeneratedValues = false) { item ->
                val plato = platos.getValue(item.platoId)
                this[PedidoItems.pedidoId] = nuevoId
                this[PedidoItems.platoId] = item.platoId
                this[PedidoItems.nombrePlato] = plato[Platos.nombre]
                this[PedidoItems.precioUnitarioCentimos] = plato[Platos.precioCentimos]
                this[PedidoItems.cantidad] = item.cantidad
                this[PedidoItems.indicaciones] = item.indicaciones.trim()
            }

            PedidoRegistrado(pedidoPorId(nuevoId), creado = true)
        }
    }

    /** Comandas para cocina, de la más antigua a la más reciente. */
    suspend fun listar(estado: EstadoPedido?): List<PedidoDto> = bd.leer {
        val consulta = if (estado != null) {
            Pedidos.selectAll().where { Pedidos.estado eq estado }
        } else {
            Pedidos.selectAll()
        }
        aPedidosDto(consulta.orderBy(Pedidos.id, SortOrder.ASC).limit(MAX_LISTADO).toList())
    }

    suspend fun cambiarEstado(pedidoId: Long, nuevo: EstadoPedido): PedidoDto = bd.escribir {
        val fila = Pedidos.selectAll().where { Pedidos.id eq pedidoId }.singleOrNull()
            ?: throw NoEncontradoException("No existe el pedido $pedidoId.")
        val actual = fila[Pedidos.estado]
        if (actual == nuevo) return@escribir aPedidoDto(fila)

        if (nuevo !in transicionesPermitidas.getValue(actual)) {
            throw ConflictoException(
                codigo = "TRANSICION_INVALIDA",
                mensaje = "Un pedido en estado $actual no puede pasar a $nuevo.",
            )
        }
        Pedidos.update({ Pedidos.id eq pedidoId }) {
            it[estado] = nuevo
            it[actualizadoEn] = reloj.millis()
        }
        pedidoPorId(pedidoId)
    }

    private fun pedidoPorId(pedidoId: Long): PedidoDto =
        aPedidoDto(Pedidos.selectAll().where { Pedidos.id eq pedidoId }.single())

    private fun aPedidoDto(fila: ResultRow): PedidoDto = aPedidosDto(listOf(fila)).single()

    /**
     * Arma los pedidos con sus líneas usando dos consultas en total (pedidos + todas sus
     * líneas), en vez de una consulta adicional por cada pedido (problema N+1).
     */
    private fun aPedidosDto(filas: List<ResultRow>): List<PedidoDto> {
        if (filas.isEmpty()) return emptyList()

        val lineasPorPedido = PedidoItems.selectAll()
            .where { PedidoItems.pedidoId inList filas.map { it[Pedidos.id] } }
            .orderBy(PedidoItems.id, SortOrder.ASC)
            .groupBy(
                keySelector = { it[PedidoItems.pedidoId] },
                valueTransform = {
                    PedidoItemDto(
                        platoId = it[PedidoItems.platoId],
                        nombrePlato = it[PedidoItems.nombrePlato],
                        precioUnitarioCentimos = it[PedidoItems.precioUnitarioCentimos],
                        cantidad = it[PedidoItems.cantidad],
                        indicaciones = it[PedidoItems.indicaciones],
                        subtotalCentimos = it[PedidoItems.precioUnitarioCentimos] * it[PedidoItems.cantidad],
                    )
                },
            )

        return filas.map { fila ->
            PedidoDto(
                id = fila[Pedidos.id],
                mesa = fila[Pedidos.mesa],
                estado = fila[Pedidos.estado],
                totalCentimos = fila[Pedidos.totalCentimos],
                creadoEn = fila[Pedidos.creadoEn],
                actualizadoEn = fila[Pedidos.actualizadoEn],
                items = lineasPorPedido[fila[Pedidos.id]].orEmpty(),
            )
        }
    }

    private fun esUuid(texto: String): Boolean =
        texto.length == 36 && runCatching { UUID.fromString(texto) }.isSuccess

    private companion object {
        const val MAX_LINEAS = 50
        const val MAX_CANTIDAD = 20
        const val MAX_LISTADO = 200

        // Máquina de estados explícita: el flujo de cocina solo avanza, y cancelar deja de
        // ser posible una vez que el plato está listo.
        val transicionesPermitidas: Map<EstadoPedido, Set<EstadoPedido>> = mapOf(
            EstadoPedido.PENDIENTE to setOf(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO),
            EstadoPedido.EN_PREPARACION to setOf(EstadoPedido.LISTO, EstadoPedido.CANCELADO),
            EstadoPedido.LISTO to setOf(EstadoPedido.ENTREGADO),
            EstadoPedido.ENTREGADO to emptySet(),
            EstadoPedido.CANCELADO to emptySet(),
        )
    }
}
