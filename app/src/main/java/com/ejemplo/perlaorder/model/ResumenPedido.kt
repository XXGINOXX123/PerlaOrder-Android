package com.ejemplo.perlaorder.model

/** Valores de clase  Resumen Pedido */

data class ResumenPedido(
    val lineas: List<LineaPedido> = emptyList(),
    val totalCentimos: Long = 0,
    val cantidadProductos: Int = 0
)

object CalculadoraPedido {
    fun subtotal(linea: LineaPedido): Long = linea.precioUnitarioCentimos * linea.cantidad
    fun total(lineas: List<LineaPedido>): Long = lineas.sumOf { subtotal(it) }
    fun cantidadProductos(lineas: List<LineaPedido>): Int = lineas.sumOf { it.cantidad }
    fun resumir(lineas: List<LineaPedido>) =
        ResumenPedido(lineas, total(lineas), cantidadProductos(lineas))
}