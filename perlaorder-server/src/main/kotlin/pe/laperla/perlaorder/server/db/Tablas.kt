package pe.laperla.perlaorder.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq

/*
 * Esquema del servidor. Replica las tablas de la app (categorias, platos) con los mismos
 * nombres de columna y añade lo que el informe dejó previsto para los sprints 2 y 3:
 * usuarios, historial_cambios, sincronizacion y pedidos.
 *
 * Convenciones:
 *  - Importes en céntimos (Long), igual que en Room (RN-02).
 *  - Fechas como epoch en milisegundos (UTC): sin zonas horarias ni parseo de texto, y
 *    viajan tal cual por JSON hasta la tablet.
 *  - `revision` es el número de versión global con el que se modificó la fila por última
 *    vez; es la base de la sincronización incremental (ver CartaRepository).
 */

enum class Rol { ADMIN, COLABORADOR }

enum class EstadoPedido { PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO }

/** Fila única (id = 1) con la identidad de esta base y el contador global de revisiones. */
object Sincronizacion : Table("sincronizacion") {
    val id = integer("id")
    val servidorId = varchar("servidor_id", 36)
    val revision = long("revision")
    override val primaryKey = PrimaryKey(id)
}

object Categorias : Table("categorias") {
    val id = integer("id")
    val nombre = varchar("nombre", 60)
    val orden = integer("orden")
    val revision = long("revision").index()
    override val primaryKey = PrimaryKey(id)
}

object Platos : Table("platos") {
    val id = integer("id").autoIncrement()
    // Sin ON DELETE CASCADE: no se puede borrar una categoría con platos (RN-01).
    val categoriaId = integer("categoria_id").references(Categorias.id).index()
    val nombre = varchar("nombre", 80)
    val descripcion = varchar("descripcion", 500)
    val ingredientes = varchar("ingredientes", 500)
    val alergenos = varchar("alergenos", 200)
    val porcion = varchar("porcion", 60)
    // La regla se valida en la capa de servicio y, además, en la base: defensa en profundidad.
    val precioCentimos = long("precio_centimos").check("ck_platos_precio") { it greater 0L }
    val imagenUrl = varchar("imagen_url", 255).nullable()
    val agotado = bool("agotado").default(false)
    val activo = bool("activo").default(true)
    val revision = long("revision").index()
    val actualizadoEn = long("actualizado_en")
    override val primaryKey = PrimaryKey(id)
}

object Usuarios : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val usuario = varchar("usuario", 40).uniqueIndex()
    val hashContrasena = varchar("hash_contrasena", 72)
    val rol = enumerationByName<Rol>("rol", 20)
    val activo = bool("activo").default(true)
    val intentosFallidos = integer("intentos_fallidos").default(0)
    val bloqueadoHasta = long("bloqueado_hasta").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Auditoría de cambios de precio, disponibilidad y estado de los platos (RN-06). */
object HistorialCambios : Table("historial_cambios") {
    val id = long("id").autoIncrement()
    val platoId = integer("plato_id").references(Platos.id).index()
    val usuarioId = integer("usuario_id").references(Usuarios.id)
    val campo = varchar("campo", 30)
    val valorAnterior = varchar("valor_anterior", 255).nullable()
    val valorNuevo = varchar("valor_nuevo", 255)
    val fecha = long("fecha")
    override val primaryKey = PrimaryKey(id)
}

object Pedidos : Table("pedidos") {
    val id = long("id").autoIncrement()
    // Generada por la tablet: reenviar el mismo pedido no lo duplica.
    val claveIdempotencia = varchar("clave_idempotencia", 36).uniqueIndex()
    val mesa = integer("mesa")
    val estado = enumerationByName<EstadoPedido>("estado", 20).index()
    val totalCentimos = long("total_centimos").check("ck_pedidos_total") { it greaterEq 0L }
    val creadoEn = long("creado_en")
    val actualizadoEn = long("actualizado_en")
    override val primaryKey = PrimaryKey(id)
}

object PedidoItems : Table("pedido_items") {
    val id = long("id").autoIncrement()
    val pedidoId = long("pedido_id").references(Pedidos.id, onDelete = ReferenceOption.CASCADE).index()
    val platoId = integer("plato_id").references(Platos.id)
    // Copia del nombre y del precio al confirmar: un cambio posterior en la carta no
    // altera lo que el comensal aceptó pagar.
    val nombrePlato = varchar("nombre_plato", 80)
    val precioUnitarioCentimos = long("precio_unitario_centimos")
    val cantidad = integer("cantidad").check("ck_items_cantidad") { it greater 0 }
    val indicaciones = varchar("indicaciones", 200).default("")
    override val primaryKey = PrimaryKey(id)
}
