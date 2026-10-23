// Destino sugerido en la app: repository/SincronizadorDeCarta.kt
// PLANTILLA: no está compilada contra tu proyecto. Ajusta package, nombres de DAO y mapeos.
package pe.laperla.perlaorder.repository

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.laperla.perlaorder.data.local.AppDatabase
import pe.laperla.perlaorder.data.local.entity.EstadoSincronizacion
import pe.laperla.perlaorder.data.network.PerlaApiService
import pe.laperla.perlaorder.data.network.dto.aEntidad
import retrofit2.HttpException
import java.io.IOException

sealed interface ResultadoSync {
    data class Exito(val platosActualizados: Int) : ResultadoSync

    /** Sin red o servidor apagado: la app sigue con Room y muestra el Snackbar de la Figura 10. */
    data object SinConexion : ResultadoSync

    data class ErrorDelServidor(val codigoHttp: Int) : ResultadoSync
}

/**
 * Trae del servidor solo lo que cambió y lo aplica a Room en una única transacción.
 *
 * La interfaz no espera a esta clase: sigue observando los Flow de Room (fuente única de
 * verdad). Cuando la transacción confirma, Room reemite y la pantalla se actualiza sola.
 * MenuRepository la invoca desde `actualizarCarta()` y, más adelante, cada 60 s (CA-03.3).
 */
class SincronizadorDeCarta(
    private val db: AppDatabase,
    private val api: PerlaApiService,
    private val ahora: () -> Long = System::currentTimeMillis,
) {
    // Evita dos sincronizaciones simultáneas (botón «Actualizar carta» + refresco periódico).
    private val enCurso = Mutex()

    suspend fun sincronizar(): ResultadoSync = enCurso.withLock {
        try {
            val local = db.sincronizacionDao().obtener()
            var remoto = api.obtenerCarta(desde = local?.revision ?: 0L)

            // La tablet estaba al día con otra base de datos (servidor reinstalado o
            // restaurado): su número de revisión ya no significa nada. Se pide todo.
            if (local != null && local.servidorId != remoto.servidorId && !remoto.completa) {
                remoto = api.obtenerCarta(desde = 0L)
            }

            // Todo o nada: si la app muere a mitad, la carta y la revisión guardada siguen
            // siendo coherentes entre sí y la siguiente sincronización retoma sin huecos.
            db.withTransaction {
                // Categorías antes que platos: la clave foránea categoria_id lo exige.
                db.platoDao().guardarCategorias(remoto.categorias.map { it.aEntidad() })
                db.platoDao().guardarPlatos(remoto.platos.map { it.aEntidad() })
                if (remoto.completa) {
                    db.platoDao().darDeBajaAusentes(remoto.platos.map { it.id })
                }
                db.sincronizacionDao().guardar(
                    EstadoSincronizacion(
                        servidorId = remoto.servidorId,
                        revision = remoto.revision,
                        ultimaSincronizacion = ahora(),
                    )
                )
            }
            ResultadoSync.Exito(platosActualizados = remoto.platos.size)
        } catch (e: IOException) {
            // Se capturan tipos concretos y no Exception: atrapar CancellationException
            // rompería la cancelación de la corrutina al salir de la pantalla.
            ResultadoSync.SinConexion
        } catch (e: HttpException) {
            ResultadoSync.ErrorDelServidor(e.code())
        }
    }
}
