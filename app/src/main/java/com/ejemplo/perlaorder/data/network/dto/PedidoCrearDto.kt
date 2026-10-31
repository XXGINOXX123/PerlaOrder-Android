package com.ejemplo.perlaorder.data.network.dto

data class PedidoCrearDto(
    val claveIdempotencia: String,
    val mesa: Int,
    val items: List<PedidoLineaCrearDto>
)

data class PedidoLineaCrearDto(
    val platoId: Int,
    val cantidad: Int,
    val indicaciones: String = ""
)

data class PedidoRegistradoDto(
    val id: Long,
    val mesa: Int,
    val totalCentimos: Long
)
