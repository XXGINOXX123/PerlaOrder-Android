// Destino sugerido en la app: data/network/PerlaApiService.kt (reemplaza o amplía el actual)
package pe.laperla.perlaorder.data.network

import pe.laperla.perlaorder.data.network.dto.CartaSyncDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Funciones `suspend`: Retrofit las ejecuta fuera del hilo principal y lanza
 * IOException (sin red / tiempo agotado) o HttpException (respuesta 4xx-5xx).
 *
 * URL base: emulador -> http://10.0.2.2:8080/   ·   tablet física -> http://<IP del servidor>:8080/
 * Ambas IP deben figurar en res/xml/network_security_config.xml (RNF-07).
 */
interface PerlaApiService {

    /** [desde] = última revisión aplicada en esta tablet; 0 pide la carta completa. */
    @GET("api/v1/carta")
    suspend fun obtenerCarta(@Query("desde") desde: Long): CartaSyncDto
}
