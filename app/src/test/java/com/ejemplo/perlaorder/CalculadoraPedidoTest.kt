package com.ejemplo.perlaorder

import com.ejemplo.perlaorder.model.CalculadoraPedido
import com.ejemplo.perlaorder.model.LineaPedido
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculadoraPedidoTest {
    private fun linea(id: Long, precio: Long, cantidad: Int) = LineaPedido(
        itemId = id, platoId = id.toInt(), nombre = "Plato $id",
        precioUnitarioCentimos = precio, cantidad = cantidad, indicaciones = ""
    )

    @Test fun pedidoVacioTieneTotalesCero() {
        val resumen = CalculadoraPedido.resumir(emptyList())
        assertEquals(0L, resumen.totalCentimos)
        assertEquals(0, resumen.cantidadProductos)
    }

    @Test fun subtotalMultiplicaPrecioPorCantidad() {
        assertEquals(6600L, CalculadoraPedido.subtotal(linea(1, 2200L, 3)))
    }

    @Test fun totalSumaTodasLasLineas() {
        val resumen = CalculadoraPedido.resumir(listOf(linea(1, 2200L, 2), linea(2, 1200L, 1)))
        assertEquals(5600L, resumen.totalCentimos)
        assertEquals(3, resumen.cantidadProductos)
    }
}
