package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.protocol.Card
import org.example.project.protocol.GameResultType
import org.example.project.protocol.ServerMessage

@Composable
fun GameOverScreen(
    gameState: ServerMessage.GameState,
    gameResult: ServerMessage.GameResult,
    onNewGame: () -> Unit,
    onShowRecords: () -> Unit,
    onShowHistory: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF2E7D32),
                        Color(0xFF1B5E20)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Barra superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎰 BLACKJACK",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onShowHistory) {
                        Text("📜", fontSize = 20.sp)
                    }
                    TextButton(onClick = onShowRecords) {
                        Text("🏆", fontSize = 20.sp)
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("❌", fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contenido principal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tarjeta de resultado
                ResultCard(gameResult)

                // Mano del Dealer
                HandDisplay(
                    title = "DEALER",
                    cards = gameResult.dealerFinalHand,
                    score = gameResult.dealerFinalScore
                )

                // Mano del Jugador
                HandDisplay(
                    title = "TU MANO",
                    cards = gameState.playerHand,
                    score = gameResult.playerFinalScore
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNewGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                    ) {
                        Text(
                            text = "🎮 Nueva Partida",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onShowHistory,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📜 Historial", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onShowRecords,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🏆 Records", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandDisplay(
    title: String,
    cards: List<Card>,
    score: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )
        Text(
            text = "Puntuación: $score",
            fontSize = 14.sp,
            color = when {
                score == 21 -> Color(0xFFFFD700)
                score > 21 -> Color(0xFFFF5252)
                else -> Color.White.copy(alpha = 0.8f)
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy((-20).dp)
        ) {
            cards.forEachIndexed { index, card ->
                CardViewSmall(card = card, elevation = index * 2)
            }
        }
    }
}

@Composable
private fun CardViewSmall(card: Card, elevation: Int = 0) {
    Card(
        modifier = Modifier
            .width(50.dp)
            .height(70.dp),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = (2 + elevation).dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.rank.symbol,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (card.suit.displayName) {
                        "Corazones", "Diamantes" -> Color.Red
                        else -> Color.Black
                    }
                )
                Text(
                    text = card.suit.symbol,
                    fontSize = 14.sp,
                    color = when (card.suit.displayName) {
                        "Corazones", "Diamantes" -> Color.Red
                        else -> Color.Black
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultCard(gameResult: ServerMessage.GameResult) {
    val (emoji, resultText, backgroundColor) = when (gameResult.result) {
        GameResultType.BLACKJACK -> Triple("🎰", "¡BLACKJACK!", Color(0xFFFFD700))
        GameResultType.WIN -> Triple("🎉", "¡GANASTE!", Color(0xFF2ECC71))
        GameResultType.LOSE -> Triple("💔", "PERDISTE", Color(0xFFE74C3C))
        GameResultType.PUSH -> Triple("🤝", "EMPATE", Color(0xFFF39C12))
        GameResultType.SURRENDER -> Triple("🏳️", "RENDICIÓN", Color(0xFF9E9E9E))
    }

    Card(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 48.sp)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = resultText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = gameResult.message,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pago y fichas
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pago",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (gameResult.payout >= 0) "+${gameResult.payout}" else "${gameResult.payout}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gameResult.payout >= 0) Color.White else Color(0xFFFFCDD2)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Fichas",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${gameResult.newChipsTotal}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
