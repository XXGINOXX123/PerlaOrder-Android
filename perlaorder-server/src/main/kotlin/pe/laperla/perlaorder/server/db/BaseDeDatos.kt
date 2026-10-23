package pe.laperla.perlaorder.server.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.sql.Connection

/**
 * Único punto de acceso a SQLite. Los repositorios no abren transacciones por su cuenta:
 * piden [leer] o [escribir] y reciben un bloque transaccional fuera del hilo de Netty.
 */
class BaseDeDatos private constructor(private val db: Database) {

    // SQLite admite un solo escritor a la vez. En lugar de depender de reintentos por
    // SQLITE_BUSY, las escrituras se serializan aquí: una transacción de escritura nunca
    // compite con otra, así que "leer y luego modificar" dentro de [escribir] es atómico.
    private val candadoDeEscritura = Mutex()

    /** Transacción de solo lectura. Con WAL no bloquea ni es bloqueada por el escritor. */
    suspend fun <T> leer(bloque: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(db) { bloque() } }

    /** Transacción de escritura: todo el bloque se confirma o se revierte en conjunto. */
    suspend fun <T> escribir(bloque: JdbcTransaction.() -> T): T =
        candadoDeEscritura.withLock {
            withContext(Dispatchers.IO) { transaction(db) { bloque() } }
        }

    companion object {
        fun conectar(rutaArchivo: String): BaseDeDatos {
            File(rutaArchivo).absoluteFile.parentFile?.mkdirs()

            // Los PRAGMA van en la URL para que el driver los aplique al abrir cada conexión:
            //  - foreign_keys: SQLite no valida claves foráneas si no se le pide.
            //  - journal_mode=WAL: las lecturas de la carta no esperan a las escrituras.
            //  - busy_timeout: espera breve en vez de fallar si el archivo está ocupado.
            val url = "jdbc:sqlite:$rutaArchivo?foreign_keys=on&journal_mode=WAL&busy_timeout=5000"

            val db = Database.connect(
                url = url,
                driver = "org.sqlite.JDBC",
                // SQLite solo admite SERIALIZABLE o READ_UNCOMMITTED.
                databaseConfig = DatabaseConfig {
                    defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                },
            )

            transaction(db) {
                SchemaUtils.create(
                    Sincronizacion, Categorias, Platos, Usuarios,
                    HistorialCambios, Pedidos, PedidoItems,
                )
            }
            return BaseDeDatos(db)
        }
    }
}
