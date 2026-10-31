package com.ejemplo.perlaorder.view.pedido

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ejemplo.perlaorder.model.ResumenPedido
import com.ejemplo.perlaorder.util.comoSoles
import com.ejemplo.perlaorder.view.theme.*

@Composable
fun PedidoScreen(
    pedido: ResumenPedido,
    enviando: Boolean,
    onVolver: () -> Unit,
    onCantidad: (Long, Int) -> Unit,
    onConfirmar: (Int) -> Unit
) {
    var mesaTexto by remember { mutableStateOf("1") }
    val mesa = mesaTexto.toIntOrNull()
    val mesaValida = mesa != null && mesa in 1..20

    Surface(modifier = Modifier.fillMaxSize(), color = CremaAdobe) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mi pedido",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MarronMadera,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onVolver) {
                    Text("Volver a la carta", color = MoradoAguayo)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Platos seleccionados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MarronMadera,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = LilaClaro)

                    if (pedido.lineas.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tu pedido está vacío. Agrega platos desde la carta.", color = MarronMadera)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(pedido.lineas, key = { it.itemId }) { linea ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.8f)) {
                                        Text(
                                            linea.nombre,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MarronMadera,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${linea.precioUnitarioCentimos.comoSoles()} por unidad",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MarronMadera
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.weight(1.15f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onCantidad(linea.itemId, linea.cantidad - 1) },
                                            enabled = !enviando,
                                            modifier = Modifier.size(36.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text("−") }
                                        Text("${linea.cantidad}", color = MarronMadera)
                                        OutlinedButton(
                                            onClick = { onCantidad(linea.itemId, linea.cantidad + 1) },
                                            enabled = !enviando && linea.cantidad < 99,
                                            modifier = Modifier.size(36.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text("+") }
                                    }

                                    Text(
                                        (linea.precioUnitarioCentimos * linea.cantidad).comoSoles(),
                                        modifier = Modifier.weight(0.9f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MoradoAguayo,
                                        maxLines = 1
                                    )
                                    IconButton(
                                        onClick = { onCantidad(linea.itemId, 0) },
                                        enabled = !enviando,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Text("✕", color = RojoLetrero, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(color = LilaClaro)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = mesaTexto,
                            onValueChange = { mesaTexto = it.filter(Char::isDigit).take(2) },
                            label = { Text("Número de mesa") },
                            singleLine = true,
                            isError = mesaTexto.isNotBlank() && !mesaValida,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.05f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Column(modifier = Modifier.weight(0.8f)) {
                            Text("Total", style = MaterialTheme.typography.bodySmall, color = MarronMadera)
                            Text(
                                pedido.totalCentimos.comoSoles(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MarronMadera,
                                maxLines = 1
                            )
                        }
                        Button(
                            onClick = { mesa?.let(onConfirmar) },
                            enabled = pedido.lineas.isNotEmpty() && !enviando && mesaValida,
                            modifier = Modifier.weight(1.15f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MoradoAguayo)
                        ) {
                            Text(
                                if (enviando) "Confirmando..." else "Confirmar pedido",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Text(
                        "El importe es referencial. El pago se realiza en el restaurante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarronMadera
                    )
                }
            }
        }
    }
}
