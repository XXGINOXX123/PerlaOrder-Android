package com.ejemplo.perlaorder

import com.ejemplo.perlaorder.util.ValidadorPlato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorPlatoTest {
    @Test fun nombreVacioEsRechazado() {
        assertEquals("El nombre del plato es obligatorio.", ValidadorPlato.errorNombre("   "))
    }

    @Test fun nombreValidoEsAceptado() {
        assertNull(ValidadorPlato.errorNombre("Ají de gallina"))
    }

    @Test fun precioConPuntoSeConvierteACentimos() {
        assertEquals(2550L, ValidadorPlato.precioACentimos("25.50").getOrThrow())
    }

    @Test fun precioConComaSeConvierteACentimos() {
        assertEquals(2550L, ValidadorPlato.precioACentimos("25,50").getOrThrow())
    }

    @Test fun precioCeroEsRechazado() {
        val resultado = ValidadorPlato.precioACentimos("0")
        assertTrue(resultado.isFailure)
        assertEquals("El precio debe ser mayor a S/ 0.00.", resultado.exceptionOrNull()?.message)
    }

    @Test fun precioConMasDeDosDecimalesEsRechazado() {
        val resultado = ValidadorPlato.precioACentimos("12.345")
        assertTrue(resultado.isFailure)
        assertEquals("El precio admite como máximo dos decimales.", resultado.exceptionOrNull()?.message)
    }

    @Test fun precioNoNumericoEsRechazado() {
        val resultado = ValidadorPlato.precioACentimos("abc")
        assertTrue(resultado.isFailure)
        assertEquals("Ingrese un precio válido (ej. 25.50).", resultado.exceptionOrNull()?.message)
    }

    @Test fun precioNegativoEsRechazado() {
        val resultado = ValidadorPlato.precioACentimos("-4.00")
        assertTrue(resultado.isFailure)
    }
}
