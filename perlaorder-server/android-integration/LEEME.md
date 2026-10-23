# Integración en la app Android (plantillas)

Estos cuatro archivos conectan el botón «Actualizar carta» con `GET /api/v1/carta`.
Son plantillas: se escribieron a partir de los nombres del informe (PlatoDao, AppDatabase,
PerlaApiService, tabla `sincronizacion`) sin acceso al código de la app, así que **no están
compiladas contra tu proyecto**. Ajusta el `package`, los nombres de propiedades de las
entidades y los métodos de los DAO.

Orden de integración:

1. `CartaSyncDto.kt` y `PerlaApiService.kt` en `data/network`.
2. `EstadoSincronizacion.kt`: nueva entidad + DAO, subir la versión de la base a 2 con su
   `Migration(1, 2)`, y añadir los tres métodos indicados a `PlatoDao`.
3. `SincronizadorDeCarta.kt` en `repository`; créalo en `PerlaOrderApp` junto al repositorio.
4. En `MenuRepository.actualizarCarta()` devuelve `sincronizador.sincronizar()`; el
   `MenuViewModel` traduce `SinConexion` al Snackbar que ya tienes (Figura 10).

Una vez que la primera sincronización termina, el servidor es el dueño de la carta: los
platos que solo existían en la tablet quedan dados de baja (no se borran). Desde ese punto,
alta, edición y baja deben llamar a la API y dejar que el cambio regrese por sincronización.
