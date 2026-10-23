package pe.laperla.perlaorder.server.db

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import pe.laperla.perlaorder.server.auth.COSTO_BCRYPT
import pe.laperla.perlaorder.server.config.AdminInicial
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

/**
 * Datos mínimos para que el servidor sea utilizable recién instalado. Cada bloque se
 * ejecuta solo si su tabla está vacía, por lo que es seguro en cada arranque.
 */
object Semilla {
    private val log = LoggerFactory.getLogger(Semilla::class.java)

    private data class PlatoSemilla(
        val categoriaId: Int,
        val nombre: String,
        val descripcion: String,
        val ingredientes: String,
        val alergenos: String,
        val porcion: String,
        val precioCentimos: Long,
    )

    suspend fun aplicar(bd: BaseDeDatos, admin: AdminInicial, reloj: Clock) = bd.escribir {
        val ahora = reloj.millis()

        if (Sincronizacion.selectAll().empty()) {
            Sincronizacion.insert {
                it[id] = 1
                it[servidorId] = UUID.randomUUID().toString()
                it[revision] = 1L
            }
        }

        if (Categorias.selectAll().empty()) {
            listOf(1 to "Entradas", 2 to "Fondos", 3 to "Bebidas").forEach { (idCategoria, nombreCategoria) ->
                Categorias.insert {
                    it[id] = idCategoria
                    it[nombre] = nombreCategoria
                    it[orden] = idCategoria
                    it[revision] = 1L
                }
            }
            cartaInicial.forEach { plato ->
                Platos.insert {
                    it[categoriaId] = plato.categoriaId
                    it[nombre] = plato.nombre
                    it[descripcion] = plato.descripcion
                    it[ingredientes] = plato.ingredientes
                    it[alergenos] = plato.alergenos
                    it[porcion] = plato.porcion
                    it[precioCentimos] = plato.precioCentimos
                    it[revision] = 1L
                    it[actualizadoEn] = ahora
                }
            }
        }

        if (Usuarios.selectAll().empty()) {
            val contrasena = admin.contrasena ?: contrasenaAleatoria().also {
                // Única vez que una contraseña aparece en claro: es temporal y debe cambiarse.
                log.warn("Administrador inicial creado -> usuario: {}  contraseña temporal: {}", admin.usuario, it)
            }
            Usuarios.insert {
                it[usuario] = admin.usuario.trim().lowercase()
                it[hashContrasena] = BCrypt.withDefaults().hashToString(COSTO_BCRYPT, contrasena.toCharArray())
                it[rol] = Rol.ADMIN
            }
        }
    }

    private fun contrasenaAleatoria(): String {
        val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val azar = SecureRandom()
        return (1..14).map { alfabeto[azar.nextInt(alfabeto.length)] }.joinToString("")
    }

    private val cartaInicial = listOf(
        PlatoSemilla(1, "Papa rellena", "Papa prensada rellena de carne, dorada al momento.",
            "Papa, carne molida, cebolla, huevo, aceituna", "Huevo", "Personal", 800),
        PlatoSemilla(1, "Causa limeña", "Causa de papa amarilla con pollo y palta.",
            "Papa amarilla, pollo, palta, mayonesa, limón", "Huevo", "Personal", 1200),
        PlatoSemilla(2, "Lomo saltado", "Lomo fino salteado al wok con papas fritas y arroz.",
            "Lomo de res, cebolla, tomate, sillao, papa, arroz", "Soya", "Personal", 2800),
        PlatoSemilla(2, "Ají de gallina", "Gallina deshilachada en crema de ají amarillo.",
            "Gallina, ají amarillo, pan, leche, pecanas, arroz", "Lácteos, gluten, frutos secos", "Personal", 2200),
        PlatoSemilla(2, "Arroz con pollo", "Arroz verde con culantro, pollo y salsa criolla.",
            "Arroz, pollo, culantro, arveja, zanahoria", "", "Para compartir", 3600),
        PlatoSemilla(3, "Chicha morada", "Bebida de maíz morado preparada en casa.",
            "Maíz morado, piña, canela, limón", "", "Vaso 400 ml", 600),
        PlatoSemilla(3, "Limonada", "Limonada fresca del día.",
            "Limón, agua, azúcar", "", "Jarra 1 L", 1200),
    )
}
