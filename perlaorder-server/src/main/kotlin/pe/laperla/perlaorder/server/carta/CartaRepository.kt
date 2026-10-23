package pe.laperla.perlaorder.server.carta

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import pe.laperla.perlaorder.server.comun.NoEncontradoException
import pe.laperla.perlaorder.server.comun.ValidacionException
import pe.laperla.perlaorder.server.comun.Validador
import pe.laperla.perlaorder.server.comun.validar
import pe.laperla.perlaorder.server.db.BaseDeDatos
import pe.laperla.perlaorder.server.db.Categorias
import pe.laperla.perlaorder.server.db.HistorialCambios
import pe.laperla.perlaorder.server.db.Platos
import pe.laperla.perlaorder.server.db.Sincronizacion
import pe.laperla.perlaorder.server.db.Usuarios
import java.time.Clock

/**
 * Carta del restaurante: lectura incremental para las tablets y mantenimiento para el personal.
 *
 * Sincronización por número de revisión
 * -------------------------------------
 * Cada escritura toma el siguiente valor de un contador global y lo graba en la fila
 * modificada. La tablet recuerda la última revisión que aplicó y pide «lo que cambió
 * después de N». Se usa un contador y no la fecha de modificación porque los relojes de
 * las tablets y del servidor no están sincronizados entre sí: un contador entero no se
 * atrasa ni se adelanta.
 *
 * La baja lógica (activo = false, RN-05) viaja como un cambio más, de modo que no hacen
 * falta «lápidas» para propagar eliminaciones.
 */
class CartaRepository(
    private val bd: BaseDeDatos,
    private val reloj: Clock,
) {

    suspend fun cambiosDesde(desde: Long): CartaSyncResponse = bd.leer {
        val meta = Sincronizacion.selectAll().where { Sincronizacion.id eq 1 }.single()
        val revisionActual = meta[Sincronizacion.revision]

        // Si la tablet dice conocer una revisión que este servidor aún no ha emitido, viene
        // de otra base (reinstalación, restauración de respaldo): se le envía la carta completa.
        val base = if (desde < 0 || desde > revisionActual) 0L else desde

        CartaSyncResponse(
            servidorId = meta[Sincronizacion.servidorId],
            revision = revisionActual,
            generadoEn = reloj.millis(),
            completa = base == 0L,
            categorias = Categorias.selectAll()
                .where { Categorias.revision greater base }
                .orderBy(Categorias.orden, SortOrder.ASC)
                .map { CategoriaDto(it[Categorias.id], it[Categorias.nombre], it[Categorias.orden]) },
            platos = Platos.selectAll()
                .where { Platos.revision greater base }
                .orderBy(Platos.id, SortOrder.ASC)
                .map(::aPlatoDto),
        )
    }

    /**
     * Catálogo completo para GET /api/platos (Actividad 08). Incluye agotados y dados de
     * baja: la app decide qué mostrar con los campos `agotado` y `activo`.
     */
    suspend fun listarPlatos(): List<PlatoDto> = bd.leer {
        Platos.selectAll()
            .orderBy(Platos.id, SortOrder.ASC)
            .map(::aPlatoDto)
    }

    suspend fun crear(solicitud: PlatoCrearRequest, usuarioId: Int): PlatoDto {
        val nombreLimpio = solicitud.nombre.trim()
        validar {
            validarNombre(nombreLimpio)
            validarPrecio(solicitud.precioCentimos)
            validarTextos(solicitud.descripcion, solicitud.ingredientes, solicitud.alergenos, solicitud.porcion)
            exigir((solicitud.imagenUrl?.length ?: 0) <= 255, "imagenUrl") { "La referencia de la imagen es demasiado larga." }
        }
        return bd.escribir {
            exigirCategoria(solicitud.categoriaId)
            val ahora = reloj.millis()
            val nuevaRevision = siguienteRevision()
            val nuevoId = Platos.insert {
                it[categoriaId] = solicitud.categoriaId
                it[nombre] = nombreLimpio
                it[descripcion] = solicitud.descripcion.trim()
                it[ingredientes] = solicitud.ingredientes.trim()
                it[alergenos] = solicitud.alergenos.trim()
                it[porcion] = solicitud.porcion.trim()
                it[precioCentimos] = solicitud.precioCentimos
                it[imagenUrl] = solicitud.imagenUrl?.trim()?.takeIf(String::isNotEmpty)
                it[revision] = nuevaRevision
                it[actualizadoEn] = ahora
            } get Platos.id
            registrarCambio(nuevoId, usuarioId, "alta", null, nombreLimpio, ahora)
            platoPorId(nuevoId)
        }
    }

    suspend fun actualizar(platoId: Int, solicitud: PlatoActualizarRequest, usuarioId: Int): PlatoDto {
        val nombreLimpio = solicitud.nombre?.trim()
        validar {
            nombreLimpio?.let { validarNombre(it) }
            solicitud.precioCentimos?.let { validarPrecio(it) }
            validarTextos(solicitud.descripcion, solicitud.ingredientes, solicitud.alergenos, solicitud.porcion)
        }
        return bd.escribir {
            val actual = filaDePlato(platoId)
            solicitud.categoriaId?.let { exigirCategoria(it) }

            // Solo se auditan los campos que realmente cambiaron (RN-06).
            val cambios = buildList<Triple<String, String, String>> {
                fun comparar(campo: String, anterior: Any, nuevo: Any?) {
                    if (nuevo != null && nuevo != anterior) add(Triple(campo, anterior.toString(), nuevo.toString()))
                }
                comparar("nombre", actual[Platos.nombre], nombreLimpio)
                comparar("precio_centimos", actual[Platos.precioCentimos], solicitud.precioCentimos)
                comparar("categoria_id", actual[Platos.categoriaId], solicitud.categoriaId)
                comparar("descripcion", actual[Platos.descripcion], solicitud.descripcion?.trim())
                comparar("ingredientes", actual[Platos.ingredientes], solicitud.ingredientes?.trim())
                comparar("alergenos", actual[Platos.alergenos], solicitud.alergenos?.trim())
                comparar("porcion", actual[Platos.porcion], solicitud.porcion?.trim())
            }
            // Petición sin cambios efectivos: no se consume una revisión ni se ensucia el historial.
            if (cambios.isEmpty()) return@escribir aPlatoDto(actual)

            val ahora = reloj.millis()
            val nuevaRevision = siguienteRevision()
            Platos.update({ Platos.id eq platoId }) { fila ->
                nombreLimpio?.let { fila[nombre] = it }
                solicitud.precioCentimos?.let { fila[precioCentimos] = it }
                solicitud.categoriaId?.let { fila[categoriaId] = it }
                solicitud.descripcion?.let { fila[descripcion] = it.trim() }
                solicitud.ingredientes?.let { fila[ingredientes] = it.trim() }
                solicitud.alergenos?.let { fila[alergenos] = it.trim() }
                solicitud.porcion?.let { fila[porcion] = it.trim() }
                fila[revision] = nuevaRevision
                fila[actualizadoEn] = ahora
            }
            cambios.forEach { (campo, anterior, nuevo) ->
                registrarCambio(platoId, usuarioId, campo, anterior, nuevo, ahora)
            }
            platoPorId(platoId)
        }
    }

    suspend fun cambiarDisponibilidad(platoId: Int, agotado: Boolean, usuarioId: Int): PlatoDto =
        cambiarBandera(platoId, usuarioId, campo = "agotado", valorNuevo = agotado)

    /** Baja lógica: el registro se conserva y deja de mostrarse en la carta (RN-05). */
    suspend fun darDeBaja(platoId: Int, usuarioId: Int): PlatoDto =
        cambiarBandera(platoId, usuarioId, campo = "activo", valorNuevo = false)

    suspend fun historial(platoId: Int): List<CambioDto> = bd.leer {
        filaDePlato(platoId)
        (HistorialCambios innerJoin Usuarios)
            .selectAll()
            .where { HistorialCambios.platoId eq platoId }
            .orderBy(HistorialCambios.id, SortOrder.DESC)
            .map {
                CambioDto(
                    campo = it[HistorialCambios.campo],
                    valorAnterior = it[HistorialCambios.valorAnterior],
                    valorNuevo = it[HistorialCambios.valorNuevo],
                    usuario = it[Usuarios.usuario],
                    fecha = it[HistorialCambios.fecha],
                )
            }
    }

    // ---------------------------------------------------------------------------------
    // Funciones internas. Todas asumen que se ejecutan dentro de una transacción abierta
    // por BaseDeDatos.leer / escribir.
    // ---------------------------------------------------------------------------------

    private suspend fun cambiarBandera(platoId: Int, usuarioId: Int, campo: String, valorNuevo: Boolean): PlatoDto =
        bd.escribir {
            val actual = filaDePlato(platoId)
            val columna = if (campo == "agotado") Platos.agotado else Platos.activo
            if (actual[columna] == valorNuevo) return@escribir aPlatoDto(actual)

            val ahora = reloj.millis()
            val nuevaRevision = siguienteRevision()
            Platos.update({ Platos.id eq platoId }) {
                it[columna] = valorNuevo
                it[revision] = nuevaRevision
                it[actualizadoEn] = ahora
            }
            registrarCambio(platoId, usuarioId, campo, actual[columna].toString(), valorNuevo.toString(), ahora)
            platoPorId(platoId)
        }

    /**
     * Incrementa y devuelve el contador global. El UPDATE va primero a propósito: toma el
     * bloqueo de escritura de SQLite antes de leer, así dos transacciones nunca obtienen
     * el mismo número.
     */
    private fun siguienteRevision(): Long {
        Sincronizacion.update({ Sincronizacion.id eq 1 }) { it[revision] = revision + 1L }
        return Sincronizacion.selectAll().where { Sincronizacion.id eq 1 }.single()[Sincronizacion.revision]
    }

    private fun registrarCambio(platoId: Int, usuarioId: Int, campo: String, anterior: String?, nuevo: String, ahora: Long) {
        HistorialCambios.insert {
            it[HistorialCambios.platoId] = platoId
            it[HistorialCambios.usuarioId] = usuarioId
            it[HistorialCambios.campo] = campo
            it[valorAnterior] = anterior
            it[valorNuevo] = nuevo
            it[fecha] = ahora
        }
    }

    private fun filaDePlato(platoId: Int): ResultRow =
        Platos.selectAll().where { Platos.id eq platoId }.singleOrNull()
            ?: throw NoEncontradoException("No existe el plato $platoId.")

    private fun platoPorId(platoId: Int): PlatoDto = aPlatoDto(filaDePlato(platoId))

    private fun exigirCategoria(categoriaId: Int) {
        if (Categorias.selectAll().where { Categorias.id eq categoriaId }.empty()) {
            throw ValidacionException("categoriaId", "La categoría seleccionada no existe.")
        }
    }

    private fun aPlatoDto(fila: ResultRow) = PlatoDto(
        id = fila[Platos.id],
        categoriaId = fila[Platos.categoriaId],
        nombre = fila[Platos.nombre],
        descripcion = fila[Platos.descripcion],
        ingredientes = fila[Platos.ingredientes],
        alergenos = fila[Platos.alergenos],
        porcion = fila[Platos.porcion],
        precioCentimos = fila[Platos.precioCentimos],
        imagenUrl = fila[Platos.imagenUrl],
        agotado = fila[Platos.agotado],
        activo = fila[Platos.activo],
        revision = fila[Platos.revision],
    )
}

// Mismas reglas que ValidadorPlato en la app (V-01 a V-04). El servidor las repite porque
// nunca debe confiar en que el cliente ya validó.
private const val PRECIO_MAXIMO_CENTIMOS = 99_999_99L

private fun Validador.validarNombre(nombre: String) {
    exigir(nombre.isNotEmpty(), "nombre") { "El nombre del plato es obligatorio." }
    exigir(nombre.length <= 80, "nombre") { "El nombre no puede superar los 80 caracteres." }
}

private fun Validador.validarPrecio(precioCentimos: Long) {
    exigir(precioCentimos > 0, "precioCentimos") { "El precio debe ser mayor a cero." }
    exigir(precioCentimos <= PRECIO_MAXIMO_CENTIMOS, "precioCentimos") { "El precio supera el máximo permitido." }
}

private fun Validador.validarTextos(
    descripcion: String?, ingredientes: String?, alergenos: String?, porcion: String?,
) {
    exigir((descripcion?.length ?: 0) <= 500, "descripcion") { "La descripción no puede superar los 500 caracteres." }
    exigir((ingredientes?.length ?: 0) <= 500, "ingredientes") { "Los ingredientes no pueden superar los 500 caracteres." }
    exigir((alergenos?.length ?: 0) <= 200, "alergenos") { "Los alérgenos no pueden superar los 200 caracteres." }
    exigir((porcion?.length ?: 0) <= 60, "porcion") { "La porción no puede superar los 60 caracteres." }
}
