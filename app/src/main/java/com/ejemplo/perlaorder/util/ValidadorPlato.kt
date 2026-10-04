package com.ejemplo.perlaorder.util

object ValidadorPlato {
    fun errorNombre(nombre: String): String? =
        if (nombre.isBlank()) "El nombre del plato es obligatorio." else null

    /** Convierte "25.50" o "25,50" a céntimos, o retorna el error de validación. */
    fun precioACentimos(texto: String): Result<Long> {
        val valor = texto.trim().replace(',', '.').toBigDecimalOrNull()
            ?: return Result.failure(IllegalArgumentException("Ingrese un precio válido (ej. 25.50)."))
        if (valor.signum() <= 0)
            return Result.failure(IllegalArgumentException("El precio debe ser mayor a S/ 0.00."))
        if (valor.stripTrailingZeros().scale() > 2)
            return Result.failure(IllegalArgumentException("El precio admite como máximo dos decimales."))
        return Result.success(valor.movePointRight(2).toLong())
    }
}