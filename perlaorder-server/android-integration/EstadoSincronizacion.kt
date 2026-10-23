// Destino sugerido en la app: data/local/entity/EstadoSincronizacion.kt y data/local/dao/SincronizacionDao.kt
// Es la tabla `sincronizacion` prevista en el informe (RN-08, CA-04.2).
//
// Al añadirla: sube `version` en @Database (1 -> 2), agrega la entidad a `entities` y
// registra una Migration(1, 2) con el CREATE TABLE. No uses fallbackToDestructiveMigration:
// borraría la carta guardada y el pedido en curso de la tablet.
package pe.laperla.perlaorder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Fila única (id = 1): de qué servidor y hasta qué revisión está al día esta tablet. */
@Entity(tableName = "sincronizacion")
data class EstadoSincronizacion(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "servidor_id") val servidorId: String,
    val revision: Long,
    /** Hora de la tablet (epoch ms) de la última sincronización correcta. */
    @ColumnInfo(name = "ultima_sincronizacion") val ultimaSincronizacion: Long,
)

@Dao
interface SincronizacionDao {
    @Query("SELECT * FROM sincronizacion WHERE id = 1")
    suspend fun obtener(): EstadoSincronizacion?

    /** Para mostrar «Última actualización: ...» en la pantalla de la carta (CA-04.2). */
    @Query("SELECT * FROM sincronizacion WHERE id = 1")
    fun observar(): Flow<EstadoSincronizacion?>

    @Upsert
    suspend fun guardar(estado: EstadoSincronizacion)
}

// --- Añadir a tu PlatoDao existente ---------------------------------------------------
//
// @Upsert
// suspend fun guardarCategorias(categorias: List<Categoria>)
//
// @Upsert
// suspend fun guardarPlatos(platos: List<Plato>)
//
// /** Tras una carga completa: lo que el servidor ya no envía deja de mostrarse (baja lógica). */
// @Query("UPDATE platos SET activo = 0 WHERE id NOT IN (:idsVigentes)")
// suspend fun darDeBajaAusentes(idsVigentes: List<Int>)
