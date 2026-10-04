package com.ejemplo.perlaorder.data.local.dao

import androidx.room.*
import com.ejemplo.perlaorder.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatoDao {
    // ---- READ ----
    @Query("SELECT * FROM categorias ORDER BY orden")
    fun observarCategorias(): Flow<List<Categoria>>

    @Query("SELECT * FROM platos WHERE activo = 1 ORDER BY agotado ASC, nombre ASC")
    fun observarPlatosActivos(): Flow<List<Plato>>

    @Query("SELECT * FROM platos WHERE categoria_id = :categoriaId AND activo = 1 ORDER BY agotado ASC, nombre ASC")
    fun observarPlatosPorCategoria(categoriaId: Int): Flow<List<Plato>>

    @Query("SELECT COUNT(*) FROM platos")
    suspend fun contarPlatos(): Int

    // ---- CREATE ----
    @Insert
    suspend fun insertar(plato: Plato): Long

    // ---- UPDATE ----
    @Update
    suspend fun actualizar(plato: Plato)

    @Query("UPDATE platos SET agotado = :agotado WHERE id = :platoId")
    suspend fun cambiarDisponibilidad(platoId: Int, agotado: Boolean)

    // ---- DELETE ----
    @Query("UPDATE platos SET activo = 0 WHERE id = :platoId")
    suspend fun darDeBaja(platoId: Int)

    @Delete
    suspend fun eliminar(plato: Plato)

    // ---- Sincronización / semilla ----
    @Upsert suspend fun guardarCategorias(categorias: List<Categoria>)
    @Upsert suspend fun guardarPlatos(platos: List<Plato>)
}