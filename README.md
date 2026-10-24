# PerlaOrder — Sistema POS & Carta Digital en Tablet (Offline-First)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Arquitectura-MVVM%20%2B%20UDF-success?style=for-the-badge)
![Room](https://img.shields.io/badge/SQLite-Room%202.8-3DDC84?style=for-the-badge&logo=sqlite&logoColor=white)
![Retrofit](https://img.shields.io/badge/API%20REST-Retrofit%203%20%2B%20Ktor-FF6F00?style=for-the-badge)

**PerlaOrder** es una aplicación nativa para Android diseñada para tablets en orientación horizontal (`sensorLandscape`), creada para digitalizar el catálogo gastronómico y la operación en salón del restaurante **La Perla del Sabor**.

---

## Problema de Negocio y Solución de Ingeniería

Los restaurantes MYPE con alta rotación en horarios punta enfrentan pérdidas operativas por el uso de cartas físicas desactualizadas, errores en la toma manual de comandas y caídas frecuentes de la red Wi-Fi del local.

**Solución implementada:**
* **Arquitectura Offline-First (Prioridad Local):** La base de datos local SQLite (**Room**) actúa como fuente única de verdad (*Single Source of Truth*). La carta permanece 100% operativa en menos de 3 segundos aun sin conexión al servidor.
* **Sincronización e Integración REST:** Consumo y operaciones CRUD conectadas a un backend local en **Ktor** mediante **Retrofit + OkHttp**, con tiempos de espera controlados (5 s) y manejo reactivo de estados de carga y errores de red.
* **Precisión Financiera:** Almacenamiento de importes en céntimos (`Long`) para eliminar errores de redondeo de punto flotante (`Double`) en el cálculo de pedidos.
* **Accesibilidad y Ergonomía en Mesa:** Cumplimiento de contraste **WCAG 2.1 AA**, tipografía mínima de `18sp`, áreas táctiles de `48x48dp` y redundancia textual (`AGOTADO`) para no depender exclusivamente del color.

---

## Arquitectura del Sistema (MVVM + Flujo Unidireccional)

```mermaid
graph TD
    UI["Capa UI (Jetpack Compose)<br/>MainActivity · MenuScreen"] -->|Eventos de usuario| VM["Capa ViewModel<br/>MenuViewModel · StateFlow#lt;MenuUiState#gt;"]
    VM -->|Estado observable| UI
    VM -->|Funciones suspend / Flow| REPO["Capa Repository<br/>MenuRepository (Fuente única de acceso)"]
    REPO -->|SQL parametrizado / Flow| ROOM["Datos Locales (Room SQLite)<br/>AppDatabase · PlatoDao · PedidoDao"]
    REPO -->|"HTTP REST JSON (Timeout 5s)"| RETROFIT["Datos Remotos (Retrofit + OkHttp)<br/>PerlaApiService · RetrofitClient"]
    RETROFIT <-->|LAN Local| KTOR["Servidor Backend REST<br/>Ktor 3 (Netty) + Exposed"]
```
