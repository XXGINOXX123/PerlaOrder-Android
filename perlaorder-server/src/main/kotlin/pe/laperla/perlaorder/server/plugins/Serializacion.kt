package pe.laperla.perlaorder.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Contrato JSON de la API. Es JSON estándar con nombres en camelCase, de modo que el
 * cliente Retrofit + Gson de la app lo consume sin adaptadores.
 */
val JsonApi = Json {
    // Tolerancia hacia adelante: una app más nueva puede enviar campos que este servidor no conoce.
    ignoreUnknownKeys = true
    // Los valores por defecto se envían siempre: Gson no conoce los defaults de Kotlin.
    encodeDefaults = true
    // Un campo nulo se omite en vez de viajar como "campo": null.
    explicitNulls = false
}

fun Application.configurarSerializacion() {
    install(ContentNegotiation) {
        json(JsonApi)
    }
}
