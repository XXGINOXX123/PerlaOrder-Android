package com.ejemplo.perlaorder.model

/** Valores de clase  Linea pedido */

data class LineaPedido(
    val itemId: Long,
    val platoId: Int,
    val nombre: String,
    val precioUnitarioCentimos: Long,
    val cantidad: Int,
    val indicaciones: String
)