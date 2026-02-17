package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import org.example.project.protocol.GamePhase
import org.example.project.protocol.ServerMessage
import org.example.project.ui.common.CardImage

@Composable
fun GameScreen(
    gameState: ServerMessage.GameState,
    onRequestCard: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit,
    onSurrender: () -> Unit,
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
            // Barra superior con fichas y apuesta
            TopGameBar(
                chips = gameState.playerChips,
                currentBet = gameState.currentBet,
                totalBet = gameState.totalBet,
                numberOfHands = gameState.numberOfHands,
                onShowRecords = onShowRecords,
                onShowHistory = onShowHistory,
                onDisconnect = onDisconnect
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mesa de juego
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Mano del Dealer
                DealerSection(
                    cards = gameState.dealerHand,
                    score = gameState.dealerScore,
                    gamePhase = gameState.gameState
                )

                // Mano del Jugador
                PlayerSection(
                    cards = gameState.playerHand,
                    score = gameState.playerScore,
                    splitHand = gameState.splitHand,
                    splitScore = gameState.splitScore,
                    activeSplitHand = gameState.activeSplitHand,
                    bustProbability = gameState.bustProbability
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controles de juego
            GameActionControls(
                gamePhase = gameState.gameState,
                canRequestCard = gameState.canRequestCard,
                canStand = gameState.canStand,
                canDouble = gameState.canDouble,
                canSplit = gameState.canSplit,
                canSurrender = gameState.canSurrender,
                onRequestCard = onRequestCard,
                onStand = onStand,
                onDouble = onDouble,
                onSplit = onSplit,
                onSurrender = onSurrender,
                onNewGame = onNewGame
            )
        }
    }
}

@Composable
private fun TopGameBar(
    chips: Int,
    currentBet: Int,
    totalBet: Int,
    numberOfHands: Int,
    onShowRecords: () -> Unit,
    onShowHistory: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Título
        Column {
            Text(
                text = "🎰 BLACKJACK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
            if (numberOfHands > 1) {
                Text(
                    text = "$numberOfHands manos",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Fichas y apuesta
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fichas
            ChipDisplay(label = "Fichas", value = chips, color = Color(0xFF2ECC71))
            
            // Apuesta actual
            ChipDisplay(
                label = if (numberOfHands > 1) "Total" else "Apuesta", 
                value = if (numberOfHands > 1) totalBet else currentBet, 
                color = Color(0xFFE74C3C)
            )
        }

        // Botones de acción
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
}

@Composable
private fun ChipDisplay(label: String, value: Int, color: Color) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "$value",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun DealerSection(
    cards: List<Card>,
    score: Int,
    gamePhase: GamePhase
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DEALER",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )
        
        Text(
            text = if (gamePhase == GamePhase.GAME_OVER) "Puntuación: $score" else "Mostrando: $score",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Cartas del dealer
        Row(
            horizontalArrangement = Arrangement.spacedBy((-30).dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            cards.forEachIndexed { index, card ->
                CardView(card = card, elevation = index * 2)
            }
        }
    }
}

@Composable
private fun PlayerSection(
    cards: List<Card>,
    score: Int,
    splitHand: List<Card>?,
    splitScore: Int?,
    activeSplitHand: Int,
    bustProbability: Double
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TU MANO",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        // Puntuación con indicador de riesgo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "Puntuación: ",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = "$score",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    score == 21 -> Color(0xFFFFD700)
                    score > 21 -> Color(0xFFFF5252)
                    else -> Color.White
                }
            )
            
            // Indicador de probabilidad de pasarse
            if (bustProbability > 0 && score < 21) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "⚠️ ${(bustProbability * 100).toInt()}% riesgo",
                    fontSize = 12.sp,
                    color = when {
                        bustProbability > 0.5 -> Color(0xFFFF5252)
                        bustProbability > 0.3 -> Color(0xFFFFA726)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }
        }

        // Cartas del jugador
        Row(
            horizontalArrangement = Arrangement.spacedBy((-30).dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            cards.forEachIndexed { index, card ->
                CardView(
                    card = card, 
                    elevation = index * 2,
                    highlighted = splitHand != null && activeSplitHand == 0
                )
            }
        }

        // Mano dividida (si existe)
        if (splitHand != null && splitScore != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "MANO DIVIDIDA (${splitScore})",
                fontSize = 14.sp,
                color = if (activeSplitHand == 1) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                fontWeight = if (activeSplitHand == 1) FontWeight.Bold else FontWeight.Normal
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy((-30).dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                splitHand.forEachIndexed { index, card ->
                    CardView(
                        card = card, 
                        elevation = index * 2,
                        highlighted = activeSplitHand == 1
                    )
                }
            }
        }
    }
}

@Composable
fun CardView(
    card: Card,
    elevation: Int = 0,
    highlighted: Boolean = false
) {
    Box(
        modifier = Modifier.offset(y = if (highlighted) (-5).dp else 0.dp)
    ) {
        CardImage(
            card = card,
            cardWidth = 70.dp,
            cardHeight = 100.dp
        )
    }
}

@Composable
private fun GameActionControls(
    gamePhase: GamePhase,
    canRequestCard: Boolean,
    canStand: Boolean,
    canDouble: Boolean,
    canSplit: Boolean,
    canSurrender: Boolean,
    onRequestCard: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit,
    onSurrender: () -> Unit,
    onNewGame: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (gamePhase) {
                GamePhase.PLAYER_TURN -> {
                    // Fila principal: PEDIR y PLANTARSE
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ActionButton(
                            text = "🎴 PEDIR",
                            enabled = canRequestCard,
                            color = Color(0xFF2ECC71),
                            modifier = Modifier.weight(1f),
                            onClick = onRequestCard
                        )
                        
                        ActionButton(
                            text = "✋ PLANTARSE",
                            enabled = canStand,
                            color = Color(0xFFF39C12),
                            modifier = Modifier.weight(1f),
                            onClick = onStand
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fila secundaria: DOBLAR, DIVIDIR, RENDIRSE
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ActionButton(
                            text = "💰 DOBLAR",
                            enabled = canDouble,
                            color = Color(0xFF3498DB),
                            modifier = Modifier.weight(1f),
                            onClick = onDouble
                        )
                        
                        ActionButton(
                            text = "✂️ DIVIDIR",
                            enabled = canSplit,
                            color = Color(0xFF9B59B6),
                            modifier = Modifier.weight(1f),
                            onClick = onSplit
                        )
                        
                        ActionButton(
                            text = "🏳️ RENDIRSE",
                            enabled = canSurrender,
                            color = Color(0xFFE74C3C),
                            modifier = Modifier.weight(1f),
                            onClick = onSurrender
                        )
                    }
                }

                GamePhase.DEALER_TURN -> {
                    Text(
                        text = "⏳ Turno del Dealer...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                GamePhase.BETTING -> {
                    Text(
                        text = "💰 Realiza tu apuesta",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                else -> {
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
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}
