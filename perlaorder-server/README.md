# PerlaOrder · servidor local (Ktor)

API REST del restaurante La Perla del Sabor. Corre en un equipo del local y atiende a las
tablets por la red Wi-Fi interna; no se expone a internet (RNF-07).

Stack: Kotlin 2.3 · Ktor 3.6 (Netty) · Exposed 1.4 · SQLite · JWT (HS256) · BCrypt.

## Ejecutar

Requiere JDK 17 o superior (sirve el JBR que trae Android Studio).

```bash
./gradlew run          # Windows: gradlew.bat run
./gradlew test         # pruebas de extremo a extremo
```

En el primer arranque se crea `data/perlaorder.db` con la carta semilla y un usuario
administrador. Si no definiste `PERLA_ADMIN_PASSWORD`, la contraseña temporal aparece una
sola vez en el log. Las variables de entorno están descritas en `.env.example`.

Comprobación rápida:

```bash
curl http://localhost:8080/health
curl "http://localhost:8080/api/v1/carta?desde=0"

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"admin","contrasena":"<tu contraseña>"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -X PUT http://localhost:8080/api/v1/platos/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"precioCentimos":900}'

curl "http://localhost:8080/api/v1/carta?desde=1"     # devuelve solo el plato 1
```

Desde el emulador de Android la URL base es `http://10.0.2.2:8080/`; desde una tablet
física, `http://<IP del equipo en la red del local>:8080/`.

## Endpoints (prefijo `/api/v1`)

| Método y ruta | Acceso | Cubre |
|---|---|---|
| `GET /health` (sin prefijo) | público | disponibilidad del servidor |
| `GET /api/platos` (sin `/v1`) | público | catálogo completo de platos; lo consume la app con Retrofit (Actividad 08) |
| `GET /carta?desde={revision}` | público | sincronización incremental (HU-04, RN-08) |
| `POST /auth/login` | público, limitado | HU-05, RN-07 |
| `GET /auth/yo` | personal | validar sesión guardada |
| `POST /platos` | ADMIN | HU-06 |
| `PUT /platos/{id}` | ADMIN | HU-07, RN-04, RN-06 |
| `PATCH /platos/{id}/disponibilidad` | ADMIN, COLABORADOR | HU-08, CA-05.5 |
| `DELETE /platos/{id}` | ADMIN | baja lógica, HU-12 |
| `GET /platos/{id}/historial` | ADMIN | RN-06 |
| `POST /pedidos` | público (tablet de mesa) | confirmación del pedido |
| `GET /pedidos?estado=PENDIENTE` | personal | comandas en cocina |
| `PATCH /pedidos/{id}/estado` | personal | avance de la comanda |

Errores: siempre `{"codigo": "...", "mensaje": "...", "detalles": {...}}`.

## Estructura

```
config/    AppConfig: variables de entorno -> objeto inmutable
db/        Tablas (Exposed), BaseDeDatos (transacciones), Semilla
comun/     ApiError, excepciones de dominio, validador
plugins/   Serialización, manejo de errores, seguridad (JWT + límite de intentos)
carta/     DTO, CartaRepository (revisiones + auditoría), rutas
auth/      AuthService (BCrypt, bloqueo, emisión de JWT), rutas
pedidos/   PedidoRepository (idempotencia, máquina de estados), rutas
android-integration/   plantillas para conectar la app (ver LEEME.md)
```

## Pendiente conocido

- Alta de usuarios colaboradores: hoy solo existe el administrador inicial.
- Subida y servicio de fotografías (HU-06, RNF-04).
- `POST /pedidos` no identifica a la tablet: cualquiera en la Wi-Fi puede enviar pedidos.
  Antes de producción, una clave por dispositivo y HTTPS (RNF-09).
- Un token sigue siendo válido hasta 8 h aunque el usuario se desactive.
