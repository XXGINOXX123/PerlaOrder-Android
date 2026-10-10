package com.ejemplo.perlaorder.data.network.dto

data class PlatoCrearRequest(
    val categoriaId: Int, val nombre: String, val precioCentimos: Long,
    val descripcion: String, val ingredientes: String, val alergenos: String, val porcion: String
)