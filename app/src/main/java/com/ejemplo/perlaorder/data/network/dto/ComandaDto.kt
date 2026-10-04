package com.ejemplo.perlaorder.data.network.dto

data class ComandaRequest(
    val comandaId: String,
    val mesa: Int,
    val items: List<ComandaItemDto>,
    val totalCentimos: Long,
    val creadaEnMillis: Long
)

data class ComandaItemDto(
    val platoId: Int,
    val nombre: String,
    val cantidad: Int,
    val precioUnitarioCentimos: Long,
    val indicaciones: String
)