package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.protocol.*
import org.example.project.ui.common.CardImage

/**
 * Pantalla de mesa PvP - Muestra todos los jugadores en la misma mesa
 * 
 * Layout:
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [Mesa: X]              [Ronda: Y]              [Fichas: Z] │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │                      🎰 DEALER 🎰                           │
 * │                         🃏 🃏                                │
 * │                      Score: 17                              │
 * │                                                             │
 * │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
 * │  │ Jugador1 │  │ Jugador2 │  │ Jugador3 │  │ Jugador4 │   │
 * │  │  (TÚ)    │  │ (Turno)  │  │ Esperando│  │ Esperando│   │
 * │  │   🃏 🃏   │  │   🃏 🃏   │  │   🃏 🃏   │  │   🃏 🃏   │   │
 * │  │ Score:15 │  │ Score:18 │  │ Score:21 │  │ Score:12 │   │
 * │  │ Bet: 50  │  │ Bet:100  │  │ Bet: 25  │  │ Bet: 75  │   │
 * │  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
 * │                                                             │
 * │           [HIT]  [STAND]  [DOUBLE]  [SURRENDER]            │
 * │                                                             │
 * └─────────────────────────────────────────────────────────────┘
 */
@Composable
fun PvPTableScreen(
    tableState: ServerMessage.PvPTableState,
    playerChips: Int,
    currentBet: Int,
    minBet: Int,
    maxBet: Int,
    gameResult: ServerMessage.GameResult?,
    // Acciones
    onPlaceBet: (Int) -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSurrender: () -> Unit,
    onContinuePlaying: () -> Unit,
    onShowRecords: () -> Unit,
    onLeaveTable: () -> Unit
) {
    // Estado local para la apuesta
    var selectedBet by remember { mutableStateOf(minBet) }
    
    // Encontrar mi info
    val myInfo = tableState.players.find { it.playerId == tableState.currentPlayerId }
    val isMyTurn = tableState.currentTurnPlayerId == tableState.currentPlayerId
    val isBettingPhase = tableState.phase == "BETTING"
    val isPlayingPhase = tableState.phase == "PLAYER_TURNS"
    val isDealerPhase = tableState.phase == "DEALER_TURN"
    val isResultPhase = tableState.phase == "RESOLVING" || tableState.phase == "ROUND_END"
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B4D3E),
                        Color(0xFF0D2818),
                        Color(0xFF061A0D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══════════════════════════════════════════════════════════════
            // BARRA SUPERIOR
            // ═══════════════════════════════════════════════════════════════
            PvPTopBar(
                tableId = tableState.tableId,
                roundNumber = tableState.roundNumber,
                playerChips = myInfo?.chips ?: playerChips,
                phase = tableState.phase,
                onShowRecords = onShowRecords,
                onLeaveTable = onLeaveTable
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DEL DEALER
            // ═══════════════════════════════════════════════════════════════
            DealerZone(
                cards = tableState.dealerCards,
                score = tableState.dealerScore,
                showScore = isDealerPhase || isResultPhase,
                isActive = isDealerPhase
            )

            Spacer(modifier = Modifier.weight(0.3f))

            // ═══════════════════════════════════════════════════════════════
            // MENSAJE DE ESTADO
            // ═══════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val statusText = when {
                    isBettingPhase -> "💰 ESPERANDO APUESTAS..."
                    isPlayingPhase && isMyTurn -> "🎴 ¡TU TURNO!"
                    isPlayingPhase -> "⏳ Turno de: ${tableState.players.find { it.isCurrentTurn }?.name ?: "..."}"
                    isDealerPhase -> "🎰 TURNO DEL DEALER..."
                    isResultPhase -> "📊 RESULTADOS"
                    else -> "⏳ Esperando..."
                }
                
                Text(
                    text = statusText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMyTurn) Color(0xFFFFD700) else Color.White,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DE JUGADORES
            // ═══════════════════════════════════════════════════════════════
            PlayersZone(
                players = tableState.players,
                currentPlayerId = tableState.currentPlayerId,
                currentTurnPlayerId = tableState.currentTurnPlayerId,
                showCards = !isBettingPhase
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DE CONTROLES
            // ═══════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f))
            ) {
                when {
                    isBettingPhase && myInfo?.status == "BETTING" -> {
                        // Controles de apuesta
                        PvPBettingControls(
                            playerChips = myInfo.chips,
                            minBet = minBet,
                            maxBet = maxBet,
                            selectedBet = selectedBet,
                            onBetChange = { selectedBet = it },
                            onPlaceBet = { onPlaceBet(selectedBet) }
                        )
                    }
                    isBettingPhase && myInfo?.status != "BETTING" -> {
                        // Ya aposté, esperando a otros
                        WaitingMessage("Esperando a que otros jugadores apuesten...")
                    }
                    isPlayingPhase && isMyTurn -> {
                        // Mis controles de juego
                        PvPPlayingControls(
                            canDouble = (myInfo?.cards?.size ?: 0) == 2,
                            canSurrender = (myInfo?.cards?.size ?: 0) == 2,
                            onHit = onHit,
                            onStand = onStand,
                            onDouble = onDouble,
                            onSurrender = onSurrender
                        )
                    }
                    isPlayingPhase && !isMyTurn -> {
                        // Esperando mi turno
                        val currentPlayer = tableState.players.find { it.isCurrentTurn }
                        WaitingMessage("Esperando a ${currentPlayer?.name ?: "otro jugador"}...")
                    }
                    isDealerPhase -> {
                        WaitingMessage("El dealer está jugando...")
                    }
                    isResultPhase -> {
                        // Mostrar mi resultado
                        PvPResultControls(
                            myInfo = myInfo,
                            dealerScore = tableState.dealerScore
                        )
                    }
                    else -> {
                        WaitingMessage("Esperando...")
                    }
                }
            }
        }

        // Overlay de resultado (si aplica)
        if (gameResult != null) {
            ResultOverlay(
                result = gameResult,
                onDismiss = onContinuePlaying
            )
        }
    }
}

@Composable
private fun PvPTopBar(
    tableId: String,
    roundNumber: Int,
    playerChips: Int,
    phase: String,
    onShowRecords: () -> Unit,
    onLeaveTable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Info de mesa
        Column {
            Text(
                text = "🎰 ${tableId.uppercase()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
            Text(
                text = "Ronda $roundNumber",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Fichas
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF2E7D32), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("💰", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$playerChips",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        // Botones
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onShowRecords) {
                Text("🏆", fontSize = 20.sp)
            }
            IconButton(onClick = onLeaveTable) {
                Text("🚪", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun DealerZone(
    cards: List<Card>,
    score: Int,
    showScore: Boolean,
    isActive: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (isActive) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "🎰 DEALER 🎰",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
            letterSpacing = 3.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (cards.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-36).dp)) {
                cards.forEach { card ->
                    CardImage(card = card, cardWidth = 100.dp, cardHeight = 140.dp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (showScore) {
                Text(
                    text = "$score",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (score > 21) Color(0xFFE74C3C) else Color.White
                )
            } else {
                Text(
                    text = "?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PlayersZone(
    players: List<PvPPlayerInfo>,
    currentPlayerId: String,
    currentTurnPlayerId: String?,
    showCards: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        players.forEach { player ->
            val isMe = player.playerId == currentPlayerId
            val isMyTurn = player.playerId == currentTurnPlayerId
            
            PlayerCard(
                player = player,
                isMe = isMe,
                isMyTurn = isMyTurn,
                showCards = showCards,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun PlayerCard(
    player: PvPPlayerInfo,
    isMe: Boolean,
    isMyTurn: Boolean,
    showCards: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isMyTurn -> Color(0xFFFFD700)
        player.isBusted -> Color(0xFFE74C3C).copy(alpha = 0.5f)
        player.isStanding -> Color(0xFF2ECC71).copy(alpha = 0.5f)
        player.isBlackjack -> Color(0xFFFFD700).copy(alpha = 0.7f)
        isMe -> Color(0xFF3498DB)
        else -> Color.White.copy(alpha = 0.2f)
    }
    
    val bgColor = when {
        isMyTurn -> Color(0xFF2E7D32).copy(alpha = 0.3f)
        isMe -> Color(0xFF1A5276).copy(alpha = 0.3f)
        else -> Color.Black.copy(alpha = 0.2f)
    }
    
    Column(
        modifier = modifier
            .widthIn(min = 100.dp, max = 140.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(
                width = if (isMyTurn || isMe) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nombre
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isMe) "👤 ${player.name}" else player.name,
                fontSize = 12.sp,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                color = if (isMe) Color(0xFF3498DB) else Color.White,
                maxLines = 1
            )
        }
        
        // Estado
        val statusText = when {
            player.isBlackjack -> "🎰 BLACKJACK"
            player.isBusted -> "💥 BUST"
            player.isStanding -> "✋ STAND"
            isMyTurn -> "🎴 JUGANDO"
            player.status == "BETTING" -> "💭 Apostando..."
            player.status == "WAITING" -> "⏳"
            else -> ""
        }
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                fontSize = 10.sp,
                color = when {
                    player.isBlackjack -> Color(0xFFFFD700)
                    player.isBusted -> Color(0xFFE74C3C)
                    player.isStanding -> Color(0xFF2ECC71)
                    else -> Color.White.copy(alpha = 0.7f)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Cartas
        if (showCards && player.cards.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
                player.cards.forEach { card ->
                    CardImage(card = card, cardWidth = 65.dp, cardHeight = 91.dp)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Score
            Text(
                text = "${player.score}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    player.isBusted -> Color(0xFFE74C3C)
                    player.score == 21 -> Color(0xFFFFD700)
                    else -> Color.White
                }
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .width(35.dp)
                    .height(49.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = Color.White.copy(alpha = 0.3f))
            }
        }
        
        // Apuesta
        if (player.currentBet > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🎯 ${player.currentBet}",
                fontSize = 11.sp,
                color = Color(0xFFE74C3C),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Fichas
        Text(
            text = "💰 ${player.chips}",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PvPBettingControls(
    playerChips: Int,
    minBet: Int,
    maxBet: Int,
    selectedBet: Int,
    onBetChange: (Int) -> Unit,
    onPlaceBet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ELIGE TU APUESTA",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700),
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Selector de apuesta
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Botón -
            IconButton(
                onClick = { if (selectedBet > minBet) onBetChange(selectedBet - 10) }
            ) {
                Text("➖", fontSize = 20.sp)
            }
            
            // Valor
            Text(
                text = "$selectedBet",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            // Botón +
            IconButton(
                onClick = { if (selectedBet < minOf(maxBet, playerChips)) onBetChange(selectedBet + 10) }
            ) {
                Text("➕", fontSize = 20.sp)
            }
        }
        
        // Fichas rápidas
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(10, 25, 50, 100).forEach { amount ->
                val canAfford = amount <= playerChips
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (canAfford) Color(0xFF8B4513) else Color.Gray.copy(alpha = 0.3f)
                        )
                        .border(2.dp, Color(0xFFFFD700), CircleShape)
                        .clickable(enabled = canAfford) { onBetChange(amount) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$amount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canAfford) Color.White else Color.Gray
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón apostar
        Button(
            onClick = onPlaceBet,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "💰 APOSTAR $selectedBet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PvPPlayingControls(
    canDouble: Boolean,
    canSurrender: Boolean,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSurrender: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎴 ¡TU TURNO!",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700),
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Fila principal
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onHit,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3498DB)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🃏 PEDIR", fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = onStand,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("✋ PLANTARSE", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Fila secundaria
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDouble,
                enabled = canDouble,
                modifier = Modifier.weight(1f).height(45.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9B59B6),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🎲 DOBLAR", fontSize = 13.sp)
            }
            
            Button(
                onClick = onSurrender,
                enabled = canSurrender,
                modifier = Modifier.weight(1f).height(45.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE74C3C),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🏳️ RENDIRSE", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PvPResultControls(
    myInfo: PvPPlayerInfo?,
    dealerScore: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (myInfo != null) {
            val (emoji, resultText, color) = when {
                myInfo.isBlackjack -> Triple("🎰", "¡BLACKJACK!", Color(0xFFFFD700))
                myInfo.isBusted -> Triple("💥", "TE PASASTE", Color(0xFFE74C3C))
                myInfo.score > dealerScore || dealerScore > 21 -> Triple("🎉", "¡GANASTE!", Color(0xFF2ECC71))
                myInfo.score < dealerScore -> Triple("💔", "PERDISTE", Color(0xFFE74C3C))
                else -> Triple("🤝", "EMPATE", Color(0xFFF39C12))
            }
            
            Text(text = emoji, fontSize = 48.sp)
            Text(
                text = resultText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${myInfo.score} vs $dealerScore",
                fontSize = 18.sp,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Siguiente ronda en breve...",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun WaitingMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⏳ $message",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultOverlay(
    result: ServerMessage.GameResult,
    onDismiss: () -> Unit
) {
    val (emoji, text, bgColor) = when (result.result) {
        GameResultType.BLACKJACK -> Triple("🎰", "¡BLACKJACK!", Color(0xFFFFD700))
        GameResultType.WIN -> Triple("🎉", "¡GANASTE!", Color(0xFF2ECC71))
        GameResultType.LOSE -> Triple("💔", "PERDISTE", Color(0xFFE74C3C))
        GameResultType.PUSH -> Triple("🤝", "EMPATE", Color(0xFFF39C12))
        GameResultType.SURRENDER -> Triple("🏳️", "RENDICIÓN", Color(0xFF9E9E9E))
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = emoji, fontSize = 48.sp)
                Text(
                    text = text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = when {
                        result.payout > 0 -> "+${result.payout} fichas"
                        result.payout < 0 -> "${result.payout} fichas"
                        else -> "±0 fichas"
                    },
                    fontSize = 18.sp,
                    color = Color.White
                )
                
                Text(
                    text = "Total: ${result.newChipsTotal}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Toca para continuar",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
