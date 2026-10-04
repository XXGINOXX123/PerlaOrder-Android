package com.ejemplo.perlaorder.data.local

import com.ejemplo.perlaorder.data.local.entity.*

object SemillaCarta {
    val categorias = listOf(
        Categoria(1, "Entradas", 1),
        Categoria(2, "Fondos", 2),
        Categoria(3, "Bebidas", 3)
    )
    val platos = listOf(
        Plato(1, 1, "Ceviche", "Pescado fresco.", "Pescado, limón, ají", "Pescado", "Personal", 2500),
        Plato(2, 2, "Lomo Saltado", "Carne al wok.", "Res, cebolla, tomate", "Gluten", "Personal", 3000),
        Plato(3, 2, "Chicharrón", "Cerdo frito.", "Cerdo, mote", "Ninguno", "Para compartir", 4500, agotado = true)
    )
}