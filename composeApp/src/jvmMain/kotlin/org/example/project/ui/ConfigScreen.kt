package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla de configuración del juego
 */
@Composable
fun ConfigScreen(
    currentDecks: Int,
    onDecksChange: (Int) -> Unit,
    currentBlackjackPayout: Double,
    onBlackjackPayoutChange: (Double) -> Unit,
    currentDealerHitsOnSoft17: Boolean,
    onDealerHitsOnSoft17Change: (Boolean) -> Unit,
    currentAllowDoubleAfterSplit: Boolean,
    onAllowDoubleAfterSplitChange: (Boolean) -> Unit,
    currentAllowSurrender: Boolean,
    onAllowSurrenderChange: (Boolean) -> Unit,
    currentMaxSplits: Int,
    onMaxSplitsChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    var selectedDecks by remember { mutableStateOf(currentDecks) }
    var selectedPayout by remember { mutableStateOf(currentBlackjackPayout) }
    var selectedSoft17 by remember { mutableStateOf(currentDealerHitsOnSoft17) }
    var selectedDoubleAfterSplit by remember { mutableStateOf(currentAllowDoubleAfterSplit) }
    var selectedSurrender by remember { mutableStateOf(currentAllowSurrender) }
    var selectedMaxSplits by remember { mutableStateOf(currentMaxSplits) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚙️ Configuración",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp, top = 32.dp)
            )

            // ── Número de mazos ──────────────────────────────────────────────
            ConfigCard(title = "Número de Mazos") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    listOf(1, 2, 4).forEach { decks ->
                        DeckOption(
                            decks = decks,
                            isSelected = selectedDecks == decks,
                            onClick = { selectedDecks = decks }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Pago de Blackjack ────────────────────────────────────────────
            ConfigCard(title = "Pago de Blackjack") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    PayoutOption(
                        label = "3:2",
                        description = "Paga 1.5×",
                        isSelected = selectedPayout == 1.5,
                        onClick = { selectedPayout = 1.5 }
                    )
                    PayoutOption(
                        label = "6:5",
                        description = "Paga 1.2×",
                        isSelected = selectedPayout == 1.2,
                        onClick = { selectedPayout = 1.2 }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Regla soft 17 ────────────────────────────────────────────────
            ConfigCard(title = "Banca en 17 blando") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "¿El dealer pide carta con 17 blando (As+6)?",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleOption(
                            label = "Pide (H17)",
                            isSelected = selectedSoft17,
                            selectedColor = Color(0xFFE74C3C),
                            onClick = { selectedSoft17 = true }
                        )
                        ToggleOption(
                            label = "Se planta (S17)",
                            isSelected = !selectedSoft17,
                            selectedColor = Color(0xFF2ECC71),
                            onClick = { selectedSoft17 = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Doblar tras dividir ──────────────────────────────────────────
            ConfigCard(title = "Doblar tras Dividir (DAS)") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    ToggleOption(
                        label = "Permitido",
                        isSelected = selectedDoubleAfterSplit,
                        selectedColor = Color(0xFF2ECC71),
                        onClick = { selectedDoubleAfterSplit = true }
                    )
                    ToggleOption(
                        label = "No permitido",
                        isSelected = !selectedDoubleAfterSplit,
                        selectedColor = Color(0xFFE74C3C),
                        onClick = { selectedDoubleAfterSplit = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Rendición ───────────────────────────────────────────────────
            ConfigCard(title = "Rendición (Surrender)") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Recupera el 50% de la apuesta al rendirse",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleOption(
                            label = "Permitida",
                            isSelected = selectedSurrender,
                            selectedColor = Color(0xFF2ECC71),
                            onClick = { selectedSurrender = true }
                        )
                        ToggleOption(
                            label = "No permitida",
                            isSelected = !selectedSurrender,
                            selectedColor = Color(0xFFE74C3C),
                            onClick = { selectedSurrender = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Número máximo de splits ──────────────────────────────────────
            ConfigCard(title = "Máximo de Divisiones (Splits)") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    listOf(1, 2, 3, 4).forEach { splits ->
                        SplitOption(
                            value = splits,
                            isSelected = selectedMaxSplits == splits,
                            onClick = { selectedMaxSplits = splits }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Botones ──────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Cancelar", color = Color.White)
                }

                Button(
                    onClick = {
                        onDecksChange(selectedDecks)
                        onBlackjackPayoutChange(selectedPayout)
                        onDealerHitsOnSoft17Change(selectedSoft17)
                        onAllowDoubleAfterSplitChange(selectedDoubleAfterSplit)
                        onAllowSurrenderChange(selectedSurrender)
                        onMaxSplitsChange(selectedMaxSplits)
                        onBack()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Componentes privados
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConfigCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            content()
        }
    }
}

@Composable
private fun DeckOption(decks: Int, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2ECC71) else Color(0xFF34495E)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$decks", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = if (decks == 1) "mazo" else "mazos",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// weight(1f) requiere RowScope — se declara como extensión de RowScope
@Composable
private fun RowScope.PayoutOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(64.dp).weight(1f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2ECC71) else Color(0xFF34495E)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun RowScope.ToggleOption(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) selectedColor else Color(0xFF34495E)
        )
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun SplitOption(value: Int, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF9B59B6) else Color(0xFF34495E)
        )
    ) {
        Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
