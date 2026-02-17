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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla para realizar apuestas antes de empezar la partida
 * Soporta selección de múltiples manos (1-3)
 */
@Composable
fun BettingScreen(
    playerChips: Int,
    minBet: Int,
    maxBet: Int,
    onPlaceBet: (Int, Int) -> Unit, // (amount, numberOfHands)
    onBack: () -> Unit
) {
    var betAmount by remember { mutableStateOf(minBet) }
    var numberOfHands by remember { mutableStateOf(1) }
    
    val actualMaxBet = minOf(maxBet, playerChips / numberOfHands)
    val totalBet = betAmount * numberOfHands
    
    // Opciones rápidas de apuesta
    val quickBets = listOf(
        minBet,
        minBet * 2,
        minBet * 5,
        minBet * 10,
        actualMaxBet
    ).filter { it <= actualMaxBet }.distinct()

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Fichas del jugador
            Card(
                modifier = Modifier.padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💰 Tus Fichas",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$playerChips",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            // Título
            Text(
                text = "Realiza tu apuesta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Selector de número de manos
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🃏 Número de Manos",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(1, 2, 3).forEach { hands ->
                            val canAfford = minBet * hands <= playerChips
                            Button(
                                onClick = { 
                                    numberOfHands = hands
                                    // Ajustar apuesta si es necesario
                                    val newMaxBet = minOf(maxBet, playerChips / hands)
                                    if (betAmount > newMaxBet) {
                                        betAmount = newMaxBet
                                    }
                                },
                                enabled = canAfford,
                                modifier = Modifier.size(70.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (numberOfHands == hands) Color(0xFF9B59B6) else Color(0xFF34495E),
                                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$hands",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (hands == 1) "mano" else "manos",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Límites de apuesta
            Text(
                text = "Mínimo: $minBet | Máximo: $actualMaxBet por mano",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Selector de apuesta
            Card(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apuesta por mano",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        // Botón menos
                        IconButton(
                            onClick = { 
                                if (betAmount > minBet) {
                                    betAmount = (betAmount - minBet).coerceAtLeast(minBet)
                                }
                            },
                            enabled = betAmount > minBet
                        ) {
                            Text(
                                text = "−",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (betAmount > minBet) Color.White else Color.Gray
                            )
                        }
                        
                        Text(
                            text = "$betAmount",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2ECC71),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        // Botón más
                        IconButton(
                            onClick = { 
                                if (betAmount < actualMaxBet) {
                                    betAmount = (betAmount + minBet).coerceAtMost(actualMaxBet)
                                }
                            },
                            enabled = betAmount < actualMaxBet
                        ) {
                            Text(
                                text = "+",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (betAmount < actualMaxBet) Color.White else Color.Gray
                            )
                        }
                    }

                    // Apuestas rápidas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        quickBets.take(4).forEach { amount ->
                            OutlinedButton(
                                onClick = { betAmount = amount },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (betAmount == amount) Color(0xFF2ECC71) else Color.White
                                )
                            ) {
                                Text(
                                    text = "$amount",
                                    fontSize = 12.sp,
                                    fontWeight = if (betAmount == amount) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    
                    // Total de apuesta
                    if (numberOfHands > 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Total: $totalBet fichas ($betAmount × $numberOfHands)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de apostar
            Button(
                onClick = { onPlaceBet(betAmount, numberOfHands) },
                enabled = totalBet <= playerChips,
                modifier = Modifier
                    .width(280.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
            ) {
                Text(
                    text = if (numberOfHands > 1) "🎲 APOSTAR $totalBet ($numberOfHands manos)" else "🎲 APOSTAR $betAmount",
                    fontSize = if (numberOfHands > 1) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBack) {
                Text(
                    text = "← Volver al menú",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
