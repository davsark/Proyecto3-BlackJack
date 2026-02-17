package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onBack: () -> Unit
) {
    var selectedDecks by remember { mutableStateOf(currentDecks) }

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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título
            Text(
                text = "⚙️ Configuración",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 48.dp, top = 32.dp)
            )

            // Número de mazos
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

            Spacer(modifier = Modifier.height(24.dp))

            // Información de reglas
            ConfigCard(title = "Reglas del Juego") {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    RuleItem("Blackjack paga", "3:2")
                    RuleItem("Dealer se planta en", "17")
                    RuleItem("Doblar permitido", "Sí")
                    RuleItem("Dividir permitido", "Sí")
                    RuleItem("Rendirse permitido", "Sí")
                    RuleItem("Fichas iniciales", "1000")
                    RuleItem("Apuesta mínima", "10")
                    RuleItem("Apuesta máxima", "500")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botones
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        onBack()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
private fun DeckOption(
    decks: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2ECC71) else Color(0xFF34495E)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$decks",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (decks == 1) "mazo" else "mazos",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun RuleItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2ECC71)
        )
    }
}
