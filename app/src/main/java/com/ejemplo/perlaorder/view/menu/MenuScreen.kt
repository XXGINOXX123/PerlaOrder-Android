package com.ejemplo.perlaorder.view.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ejemplo.perlaorder.data.local.entity.Plato
import com.ejemplo.perlaorder.util.comoSoles
import com.ejemplo.perlaorder.view.theme.*
import com.ejemplo.perlaorder.viewmodel.EstadoOperacion
import com.ejemplo.perlaorder.viewmodel.MenuUiState
import com.ejemplo.perlaorder.viewmodel.MenuViewModel
import com.ejemplo.perlaorder.view.pedido.PedidoScreen

/** Rojo del Snackbar de error de conexión (Actividad 09). */
private val RojoError = Color(0xFFB3261E)

/** Snackbar con un dato extra: si es de error se pinta de rojo y dura más para poder capturarlo. */
private class VisualesPerla(
    override val message: String,
    val esError: Boolean
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = esError
    override val duration: SnackbarDuration = if (esError) SnackbarDuration.Long else SnackbarDuration.Short
}

@Composable
fun MenuScreen(viewModel: MenuViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pedido by viewModel.pedido.collectAsStateWithLifecycle()
    val enviandoPedido by viewModel.enviandoPedido.collectAsStateWithLifecycle()
    val mensajePedido by viewModel.mensajePedido.collectAsStateWithLifecycle()
    var mostrarCarrito by remember { mutableStateOf(false) }
    var vistaCliente by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    var platoEnEdicion by remember { mutableStateOf<Plato?>(null) }
    LaunchedEffect(mensajePedido) {
        mensajePedido?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajePedido()
        }
    }

    // Estados Exito / Falla -> Snackbar (normal o rojo) y luego se vuelve a Inactivo.
    LaunchedEffect(state.operacion) {
        when (val op = state.operacion) {
            is EstadoOperacion.Exito -> snackbarHostState.showSnackbar(VisualesPerla(op.mensaje, esError = false))
            is EstadoOperacion.Falla -> snackbarHostState.showSnackbar(VisualesPerla(op.mensaje, esError = true))
            else -> return@LaunchedEffect
        }
        viewModel.operacionAtendida()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { datos ->
                val esError = (datos.visuals as? VisualesPerla)?.esError == true
                val contenedor = if (esError) RojoError else SnackbarDefaults.color
                val contenido = if (esError) Color.White else SnackbarDefaults.contentColor
                Snackbar(
                    snackbarData = datos,
                    containerColor = contenedor,
                    contentColor = contenido,
                    dismissActionContentColor = contenido
                )
            }
        },
        topBar = { Encabezado(
            onActualizar = viewModel::actualizarCarta,
            onPedido = { mostrarCarrito = true },
            cantidad = pedido.cantidadProductos,
            vistaCliente = vistaCliente,
            onCambiarVista = { destinoCliente ->
                if (destinoCliente) { vistaCliente = true; mostrarCarrito = false }
                else if (viewModel.haySesionAdministrativa()) { vistaCliente = false; mostrarCarrito = false }
                else viewModel.solicitarAccesoAdministrativo()
            }
        ) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (mostrarCarrito) {
                PedidoScreen(pedido, enviandoPedido, onVolver = { mostrarCarrito = false },
                    onCantidad = viewModel::cambiarCantidad, onConfirmar = viewModel::confirmarPedido)
            } else if (vistaCliente) {
                CartaPlatos(
                    state, viewModel::seleccionarCategoria, viewModel::alternarDisponibilidad,
                    { platoEnEdicion = it }, viewModel::darDeBaja, viewModel::agregarAlCarrito,
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                    vistaCliente = true
                )
            } else Row(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormularioPlato(state, viewModel::agregarPlato, Modifier.weight(0.35f))
                CartaPlatos(
                    state, viewModel::seleccionarCategoria, viewModel::alternarDisponibilidad,
                    { platoEnEdicion = it }, viewModel::darDeBaja, viewModel::agregarAlCarrito,
                    Modifier.weight(0.65f).fillMaxHeight(),
                    vistaCliente = false
                )
            }
            // Estado Cargando -> indicador de progreso sobre toda la pantalla.
            val operacion = state.operacion
            if (operacion is EstadoOperacion.Cargando) IndicadorCarga(operacion.detalle)
        }
    }

    platoEnEdicion?.let { plato ->
        DialogoEdicion(
            plato = plato,
            onCerrar = { platoEnEdicion = null },
            onGuardar = { nombre, precio -> viewModel.editarPlato(plato, nombre, precio) }
        )
    }

    // Diálogo de acceso: aparece cuando se intenta guardar un plato sin sesión iniciada.
    if (state.mostrarLogin) {
        DialogoLogin(
            error = state.errorLogin,
            enviando = state.enviandoLogin,
            onCerrar = viewModel::cerrarLogin,
            onIngresar = viewModel::iniciarSesion
        )
    }
}

@Composable
private fun Encabezado(
    onActualizar: () -> Unit,
    onPedido: () -> Unit,
    cantidad: Int,
    vistaCliente: Boolean,
    onCambiarVista: (Boolean) -> Unit
) {
    Column {
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("La Perla del Sabor", style = MaterialTheme.typography.headlineSmall)
                    Text(if (vistaCliente) "Carta digital · Haz tu pedido" else "Administración · Gestión de carta")
                }
                TextButton(onClick = { onCambiarVista(!vistaCliente) }) {
                    Text(if (vistaCliente) "Administración" else "Vista cliente", color = Color.White)
                }
                Button(
                    onClick = onActualizar,
                    colors = ButtonDefaults.buttonColors(containerColor = AmarilloMadera, contentColor = MarronMadera)
                ) { Text("Actualizar") }
                if (vistaCliente) {
                    Button(onClick = onPedido,
                        colors = ButtonDefaults.buttonColors(containerColor = AmarilloMadera, contentColor = MarronMadera)) {
                        Text("Mi pedido ($cantidad)")
                    }
                }
            }
        }
        BandaAguayo()
    }
}

/** Capa semitransparente con CircularProgressIndicator mientras hay una petición al servidor. */
@Composable
private fun IndicadorCarga(detalle: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Consume los toques para que no se pueda pulsar nada mientras se envía.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CircularProgressIndicator()
                Text(detalle, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Franja decorativa inspirada en los manteles de aguayo del local. */
@Composable
private fun BandaAguayo() {
    val colores = listOf(MoradoAguayo, MagentaAguayo, NaranjaAguayo, AmarilloMadera, TurquesaAguayo, VerdeLetrero)
    Row(Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { }) {
        repeat(30) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(colores[i % colores.size])) }
    }
}

@Composable
private fun ChipPerla(seleccionado: Boolean, onClick: () -> Unit, texto: String) {
    FilterChip(
        selected = seleccionado, onClick = onClick, label = { Text(texto) },
        modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun FormularioPlato(
    state: MenuUiState,
    onGuardar: (String, String, String, Int?) -> Boolean,
    modifier: Modifier
) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var categoriaElegida by remember { mutableStateOf<Int?>(null) }
    val categoriaId = categoriaElegida ?: state.categorias.firstOrNull()?.id

    // El formulario se limpia solo cuando el servidor confirma que registró el plato.
    LaunchedEffect(state.platosRegistrados) {
        if (state.platosRegistrados > 0) { nombre = ""; descripcion = ""; precio = "" }
    }

    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nuevo plato", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(precio, { precio = it }, label = { Text("Precio (S/)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())
            Text("Categoría")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categorias.forEach { c ->
                    ChipPerla(categoriaId == c.id, { categoriaElegida = c.id }, c.nombre)
                }
            }
            Button(
                onClick = { onGuardar(nombre, descripcion, precio, categoriaId) },
                enabled = !state.cargando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (state.cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.cargando) "Enviando..." else "Guardar plato")
            }
            state.mensajeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun CartaPlatos(
    state: MenuUiState,
    onCategoria: (Int?) -> Unit,
    onAlternar: (Plato) -> Unit,
    onEditar: (Plato) -> Unit,
    onBaja: (Plato) -> Unit,
    onAgregar: (Plato) -> Unit,
    modifier: Modifier,
    vistaCliente: Boolean
) {
    Column(modifier) {
        Text(if (vistaCliente) "Nuestra carta" else "Carta", style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipPerla(state.categoriaSeleccionada == null, { onCategoria(null) }, "Todas")
            state.categorias.forEach { c ->
                ChipPerla(state.categoriaSeleccionada == c.id, { onCategoria(c.id) }, c.nombre)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.platos, key = { it.id }) { plato ->
                TarjetaPlato(plato, onAlternar, onEditar, onBaja, onAgregar, vistaCliente)
            }
        }
    }
}

@Composable
private fun TarjetaPlato(
    plato: Plato,
    onAlternar: (Plato) -> Unit,
    onEditar: (Plato) -> Unit,
    onBaja: (Plato) -> Unit,
    onAgregar: (Plato) -> Unit,
    vistaCliente: Boolean
) {
    Card(
        Modifier.fillMaxWidth().alpha(if (plato.agotado) 0.7f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plato.nombre, style = MaterialTheme.typography.titleMedium)
            if (plato.descripcion.isNotBlank()) Text(plato.descripcion, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(plato.precioCentimos.comoSoles(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (plato.agotado) Text("AGOTADO", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (vistaCliente) {
                    Button(onClick = { onAgregar(plato) }, enabled = !plato.agotado && plato.activo) {
                        Text("+ Agregar al pedido")
                    }
                }
            }
            if (!vistaCliente) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ onAlternar(plato) }) { Text(if (plato.agotado) "Disponible" else "Agotado") }
                    TextButton({ onEditar(plato) }) { Text("Editar") }
                    TextButton({ onBaja(plato) }) { Text("Dar de baja") }
                }
            }
        }
    }
}

@Composable
private fun DialogoEdicion(
    plato: Plato,
    onCerrar: () -> Unit,
    onGuardar: (String, String) -> String?
) {
    var nombre by remember { mutableStateOf(plato.nombre) }
    var precio by remember { mutableStateOf(plato.precioCentimos.comoSoles().removePrefix("S/ ")) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Editar plato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(precio, { precio = it }, label = { Text("Precio (S/)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resultado = onGuardar(nombre, precio)
                    if (resultado == null) onCerrar() else error = resultado
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoLogin(
    error: String?,
    enviando: Boolean,
    onCerrar: () -> Unit,
    onIngresar: (String, String) -> Unit
) {
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Acceso del personal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Para registrar platos en el servidor, inicie sesión como administrador.")
                OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true)
                OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIngresar(usuario, contrasena) },
                enabled = !enviando,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(if (enviando) "Ingresando..." else "Iniciar sesión") }
        },
        dismissButton = { TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
    )
}