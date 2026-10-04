package com.ejemplo.perlaorder.data.network.dto

import com.ejemplo.perlaorder.data.local.entity.Plato

/**
 * Forma exacta de cada elemento del arreglo JSON que devuelve GET /api/platos.
 * Los nombres coinciden 1:1 con las claves del JSON, por eso Gson no necesita @SerializedName.
 * El campo "revision" del servidor no se declara: Gson ignora las claves que no conoce.
 */
data class PlatoDto(
    val id: Int,
    val categoriaId: Int,
    val nombre: String,
    val descripcion: String,
    val ingredientes: String,
    val alergenos: String,
    val porcion: String,
    val precioCentimos: Long,
    val imagenUrl: String?,      // el servidor omite la clave cuando no hay foto
    val agotado: Boolean,
    val activo: Boolean
)

/** Traduce el contrato de red a la entidad que guarda Room. */
fun PlatoDto.aEntidad(): Plato = Plato(
    id = id,
    categoriaId = categoriaId,
    nombre = nombre,
    descripcion = descripcion,
    ingredientes = ingredientes,
    alergenos = alergenos,
    porcion = porcion,
    precioCentimos = precioCentimos,
    imagenUrl = imagenUrl,
    agotado = agotado,
    activo = activo
)