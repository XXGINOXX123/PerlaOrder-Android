package com.ejemplo.perlaorder.view.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ejemplo.perlaorder.data.local.entity.Plato
import com.ejemplo.perlaorder.util.comoSoles
import com.ejemplo.perlaorder.view.theme.*
import com.ejemplo.perlaorder.viewmodel.MenuUiState
import com.ejemplo.perlaorder.viewmodel.MenuViewModel

@Composable
fun MenuScreen(viewModel: MenuViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var platoEnEdicion by remember { mutableStateOf<Plato?>(null) }

    LaunchedEffect(state.mensajeInfo) {
        state.mensajeInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { Encabezado(onActualizar = viewModel::actualizarCarta) }
    ) { padding ->
        Row(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormularioPlato(state, viewModel::agregarPlato, Modifier.weight(0.35f))
            CartaPlatos(
                state, viewModel::seleccionarCategoria, viewModel::alternarDisponibilidad,
                { platoEnEdicion = it }, viewModel::darDeBaja,
                Modifier.weight(0.65f).fillMaxHeight()
            )
        }
    }

    platoEnEdicion?.let { plato ->
        DialogoEdicion(
            plato = plato,
            onCerrar = { platoEnEdicion = null },
            onGuardar = { nombre, precio -> viewModel.editarPlato(plato, nombre, precio) }
        )
    }
}

@Composable
private fun Encabezado(onActualizar: () -> Unit) {
    Column {
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("La Perla del Sabor", style = MaterialTheme.typography.headlineSmall)
                    Text("Comida típica regional · Carta digital")
                }
                Button(
                    onClick = onActualizar,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmarilloMadera, contentColor = MarronMadera)
                ) { Text("Actualizar carta") }
            }
        }
        BandaAguayo()
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
                onClick = {
                    if (onGuardar(nombre, descripcion, precio, categoriaId)) {
                        nombre = ""; descripcion = ""; precio = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Guardar plato") }
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
    modifier: Modifier
) {
    Column(modifier) {
        Text("Carta", style = MaterialTheme.typography.headlineSmall)
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
                TarjetaPlato(plato, onAlternar, onEditar, onBaja)
            }
        }
    }
}

@Composable
private fun TarjetaPlato(
    plato: Plato,
    onAlternar: (Plato) -> Unit,
    onEditar: (Plato) -> Unit,
    onBaja: (Plato) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().alpha(if (plato.agotado) 0.7f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plato.nombre, style = MaterialTheme.typography.titleMedium)
                Text(plato.descripcion)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            plato.precioCentimos.comoSoles(),
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (plato.agotado)   // texto, no solo color (RNF-13)
                        Text("AGOTADO", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            TextButton({ onAlternar(plato) }, Modifier.heightIn(min = 48.dp)) {
                Text(if (plato.agotado) "Marcar disponible" else "Marcar agotado")
            }
            TextButton({ onEditar(plato) }, Modifier.heightIn(min = 48.dp)) { Text("Editar") }
            TextButton({ onBaja(plato) }, Modifier.heightIn(min = 48.dp)) { Text("Dar de baja") }
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