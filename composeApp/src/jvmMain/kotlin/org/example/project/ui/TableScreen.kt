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
 * Pantalla de mesa unificada - Flujo continuo estilo casino
 * 
 * Fases:
 * 1. BETTING - Esperando apuesta del jugador
 * 2. PLAYING - Turno del jugador (Hit/Stand/Double/Split/Surrender)
 * 3. DEALER_TURN - El dealer juega
 * 4. RESULT - Mostrando resultado (transición automática a BETTING)
 */
@Composable
fun TableScreen(
    // Estado
    tablePhase: TablePhase,
    playerChips: Int,
    currentBet: Int,
    lastBet: Int,
    minBet: Int,
    maxBet: Int,
    gameState: ServerMessage.GameState?,
    gameResult: ServerMessage.GameResult?,
    
    // Acciones de apuesta
    onPlaceBet: (amount: Int, hands: Int) -> Unit,
    onRepeatLastBet: () -> Unit,
    
    // Acciones de juego
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit,
    onSurrender: () -> Unit,
    
    // Acciones de navegación
    onContinuePlaying: () -> Unit,
    onShowRecords: () -> Unit,
    onShowHistory: () -> Unit,
    onLeaveTable: () -> Unit
) {
    var selectedBet by remember { mutableStateOf(lastBet.coerceIn(minBet, maxBet)) }
    var numberOfHands by remember { mutableStateOf(1) }
    var showResultOverlay by remember { mutableStateOf(false) }
    
    // Mostrar overlay cuando hay resultado
    LaunchedEffect(tablePhase) {
        showResultOverlay = tablePhase == TablePhase.RESULT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D4F21),
                        Color(0xFF1B5E20),
                        Color(0xFF2E7D32),
                        Color(0xFF1B5E20),
                        Color(0xFF0D4F21)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // BARRA SUPERIOR - Siempre visible
            // ═══════════════════════════════════════════════════════════════
            TableTopBar(
                playerChips = playerChips,
                currentBet = if (tablePhase == TablePhase.BETTING) 0 else currentBet,
                onShowRecords = onShowRecords,
                onShowHistory = onShowHistory,
                onLeaveTable = onLeaveTable
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DEL DEALER
            // ═══════════════════════════════════════════════════════════════
            DealerZone(
                cards = gameState?.dealerHand ?: emptyList(),
                score = gameState?.dealerScore ?: 0,
                showScore = tablePhase == TablePhase.RESULT || tablePhase == TablePhase.DEALER_TURN,
                isActive = tablePhase == TablePhase.DEALER_TURN
            )

            Spacer(modifier = Modifier.weight(0.3f))

            // ═══════════════════════════════════════════════════════════════
            // ZONA CENTRAL - Mensaje de estado
            // ═══════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (tablePhase) {
                    TablePhase.BETTING -> {
                        Text(
                            text = "💰 REALIZA TU APUESTA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            letterSpacing = 2.sp
                        )
                    }
                    TablePhase.PLAYING -> {
                        Text(
                            text = "🎴 TU TURNO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                    TablePhase.DEALER_TURN -> {
                        Text(
                            text = "🎰 TURNO DEL DEALER...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFA726),
                            letterSpacing = 2.sp
                        )
                    }
                    TablePhase.RESULT -> {
                        // Se muestra en overlay
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DEL JUGADOR
            // ═══════════════════════════════════════════════════════════════
            PlayerZone(
                cards = gameState?.playerHand ?: emptyList(),
                score = gameState?.playerScore ?: 0,
                splitHand = gameState?.splitHand,
                splitScore = gameState?.splitScore,
                bustProbability = gameState?.bustProbability ?: 0.0,
                isActive = tablePhase == TablePhase.PLAYING,
                showCards = tablePhase != TablePhase.BETTING,
                multipleHands = gameState?.multipleHands ?: emptyList(),
                activeHandIndex = gameState?.activeHandIndex ?: 0
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // ZONA DE CONTROLES - Cambia según la fase
            // ═══════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f))
            ) {
                when (tablePhase) {
                    TablePhase.BETTING -> {
                        BettingControls(
                            playerChips = playerChips,
                            minBet = minBet,
                            maxBet = maxBet,
                            selectedBet = selectedBet,
                            numberOfHands = numberOfHands,
                            lastBet = lastBet,
                            onBetChange = { selectedBet = it },
                            onHandsChange = { 
                                numberOfHands = it
                                // Ajustar apuesta si es necesario
                                val newMaxBet = minOf(maxBet, playerChips / it)
                                if (selectedBet > newMaxBet) {
                                    selectedBet = newMaxBet
                                }
                            },
                            onPlaceBet = { onPlaceBet(selectedBet, numberOfHands) },
                            onRepeatLastBet = {
                                if (lastBet in minBet..minOf(maxBet, playerChips)) {
                                    onPlaceBet(lastBet, 1)
                                }
                            }
                        )
                    }
                    
                    TablePhase.PLAYING -> {
                        PlayingControls(
                            canHit = gameState?.canRequestCard ?: false,
                            canStand = gameState?.canStand ?: false,
                            canDouble = gameState?.canDouble ?: false,
                            canSplit = gameState?.canSplit ?: false,
                            canSurrender = gameState?.canSurrender ?: false,
                            onHit = onHit,
                            onStand = onStand,
                            onDouble = onDouble,
                            onSplit = onSplit,
                            onSurrender = onSurrender
                        )
                    }
                    
                    TablePhase.DEALER_TURN -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    TablePhase.RESULT -> {
                        ResultControls(
                            canContinue = playerChips >= minBet,
                            lastBet = lastBet,
                            playerChips = playerChips,
                            minBet = minBet,
                            maxBet = maxBet,
                            onContinue = onContinuePlaying,
                            onRepeatBet = {
                                if (lastBet in minBet..minOf(maxBet, playerChips)) {
                                    onPlaceBet(lastBet, 1)
                                } else {
                                    onContinuePlaying()
                                }
                            },
                            onLeaveTable = onLeaveTable
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // OVERLAY DE RESULTADO
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showResultOverlay && gameResult != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            gameResult?.let { result ->
                ResultOverlay(
                    result = result,
                    onDismiss = { showResultOverlay = false }
                )
            }
        }
    }
}

/**
 * Fases de la mesa
 */
enum class TablePhase {
    BETTING,      // Esperando apuesta
    PLAYING,      // Turno del jugador
    DEALER_TURN,  // Turno del dealer
    RESULT        // Mostrando resultado
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPONENTES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TableTopBar(
    playerChips: Int,
    currentBet: Int,
    onShowRecords: () -> Unit,
    onShowHistory: () -> Unit,
    onLeaveTable: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo
        Text(
            text = "🎰 BLACKJACK",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )

        // Fichas
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipBadge(label = "💰", value = playerChips, color = Color(0xFF2ECC71))
            if (currentBet > 0) {
                ChipBadge(label = "🎯", value = currentBet, color = Color(0xFFE74C3C))
            }
        }

        // Botones
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onShowHistory) {
                Text("📜", fontSize = 18.sp)
            }
            IconButton(onClick = onShowRecords) {
                Text("🏆", fontSize = 18.sp)
            }
            IconButton(onClick = onLeaveTable) {
                Text("🚪", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ChipBadge(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF2C3E50), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DEALER",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
            letterSpacing = 3.sp
        )
        
        if (showScore && score > 0) {
            Text(
                text = "$score",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    score > 21 -> Color(0xFFFF5252)
                    score == 21 -> Color(0xFFFFD700)
                    else -> Color.White
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (cards.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-45).dp)
            ) {
                cards.forEachIndexed { index, card ->
                    CardImage(
                        card = card,
                        cardWidth = 110.dp,
                        cardHeight = 154.dp
                    )
                }
            }
        } else {
            // Placeholder compacto (solo durante la fase de apuesta)
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(100.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun PlayerZone(
    cards: List<Card>,
    score: Int,
    splitHand: List<Card>?,
    splitScore: Int?,
    bustProbability: Double,
    isActive: Boolean,
    showCards: Boolean,
    // Múltiples manos
    multipleHands: List<MultiHandState> = emptyList(),
    activeHandIndex: Int = 0
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (multipleHands.size > 1) {
            // MODO MÚLTIPLES MANOS
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                multipleHands.forEachIndexed { index, handState ->
                    val isActiveHand = index == activeHandIndex && handState.status == HandStatus.PLAYING
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isActiveHand) Color(0xFF2E7D32).copy(alpha = 0.3f)
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isActiveHand) 3.dp else 1.dp,
                                color = when {
                                    isActiveHand -> Color(0xFFFFD700)
                                    handState.status == HandStatus.BUSTED -> Color(0xFFE74C3C).copy(alpha = 0.5f)
                                    handState.status == HandStatus.STANDING -> Color(0xFF2ECC71).copy(alpha = 0.5f)
                                    handState.status == HandStatus.BLACKJACK -> Color(0xFFFFD700).copy(alpha = 0.5f)
                                    else -> Color.White.copy(alpha = 0.2f)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Etiqueta de la mano
                        Text(
                            text = "MANO ${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActiveHand) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        
                        // Apuesta de esta mano
                        Text(
                            text = "🎯 ${handState.bet}",
                            fontSize = 10.sp,
                            color = Color(0xFFE74C3C)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Cartas de esta mano
                        if (handState.cards.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-27).dp)
                            ) {
                                handState.cards.forEach { card ->
                                    CardImage(
                                        card = card,
                                        cardWidth = 80.dp,
                                        cardHeight = 112.dp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Puntuación
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${handState.score}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    handState.status == HandStatus.BUSTED -> Color(0xFFE74C3C)
                                    handState.score == 21 -> Color(0xFFFFD700)
                                    else -> Color.White
                                }
                            )
                            
                            // Estado de la mano
                            when (handState.status) {
                                HandStatus.BUSTED -> {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("💥", fontSize = 14.sp)
                                }
                                HandStatus.STANDING -> {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("✋", fontSize = 14.sp)
                                }
                                HandStatus.BLACKJACK -> {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎰", fontSize = 14.sp)
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Indicador de mano activa
            if (isActive) {
                Text(
                    text = "▼ JUGANDO MANO ${activeHandIndex + 1} ▼",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    letterSpacing = 2.sp
                )
            }
            
        } else {
            // MODO MANO ÚNICA (comportamiento original)
            if (showCards && cards.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-45).dp)
                ) {
                    cards.forEachIndexed { index, card ->
                        CardImage(
                            card = card,
                            cardWidth = 115.dp,
                            cardHeight = 161.dp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$score",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            score > 21 -> Color(0xFFFF5252)
                            score == 21 -> Color(0xFFFFD700)
                            else -> Color.White
                        }
                    )
                    
                    if (bustProbability > 0 && score < 21 && isActive) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "⚠️ ${(bustProbability * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = when {
                                bustProbability > 0.5 -> Color(0xFFFF5252)
                                bustProbability > 0.3 -> Color(0xFFFFA726)
                                else -> Color(0xFF4CAF50)
                            }
                        )
                    }
                }
            }
            // Cuando showCards=false (fase de apuesta) no mostramos placeholder para liberar espacio
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "TU MANO",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun BettingControls(
    playerChips: Int,
    minBet: Int,
    maxBet: Int,
    selectedBet: Int,
    numberOfHands: Int,
    lastBet: Int,
    onBetChange: (Int) -> Unit,
    onHandsChange: (Int) -> Unit,
    onPlaceBet: () -> Unit,
    onRepeatLastBet: () -> Unit
) {
    val actualMaxBet = minOf(maxBet, playerChips / numberOfHands)
    val totalBet = selectedBet * numberOfHands

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fichas de casino - clickeables para agregar
        Text(
            text = "Toca una ficha para agregarla:",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Fila: [Repetir] [10] [25] [50] [100] [500] [REPARTIR]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón Repetir (izquierda)
            val showRepeat = lastBet in minBet..minOf(maxBet, playerChips)
            if (showRepeat) {
                Button(
                    onClick = onRepeatLastBet,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3498DB))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔄", fontSize = 12.sp)
                        Text("$lastBet", fontSize = 8.sp, color = Color.White)
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Fichas de casino (centro)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    10 to Color(0xFF2196F3),
                    25 to Color(0xFF4CAF50),
                    50 to Color(0xFFE91E63),
                    100 to Color(0xFF9C27B0),
                    500 to Color(0xFFFF9800)
                ).forEach { (chip, color) ->
                    val canAfford = selectedBet + chip <= actualMaxBet
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (canAfford) color else Color.Gray.copy(alpha = 0.3f))
                            .border(3.dp, Color(0xFFFFD700), CircleShape)
                            .clickable(enabled = canAfford) {
                                onBetChange(minOf(selectedBet + chip, actualMaxBet))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chip >= 100) "${chip/100}00" else "$chip",
                            fontSize = if (chip >= 100) 11.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canAfford) Color.White else Color.Gray
                        )
                    }
                }
            }

            // Botón REPARTIR (derecha)
            Button(
                onClick = onPlaceBet,
                enabled = totalBet <= playerChips && selectedBet >= minBet,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ECC71),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎴", fontSize = 12.sp)
                    Text(
                        text = "DAR!",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totalBet <= playerChips && selectedBet >= minBet) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Apuesta actual con controles +/-
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Botón reset
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE74C3C))
                    .clickable { onBetChange(minBet) },
                contentAlignment = Alignment.Center
            ) {
                Text("↺", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Botón -
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selectedBet > minBet) Color(0xFF3498DB) else Color.Gray.copy(alpha = 0.3f))
                    .clickable(enabled = selectedBet > minBet) { 
                        onBetChange(maxOf(selectedBet - 10, minBet))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            // Valor de apuesta
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "$selectedBet",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2ECC71)
                )
                if (numberOfHands > 1) {
                    Text(
                        text = "× $numberOfHands = $totalBet total",
                        fontSize = 12.sp,
                        color = Color(0xFFFFD700)
                    )
                }
            }
            
            // Botón +
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selectedBet < actualMaxBet) Color(0xFF3498DB) else Color.Gray.copy(alpha = 0.3f))
                    .clickable(enabled = selectedBet < actualMaxBet) { 
                        onBetChange(minOf(selectedBet + 10, actualMaxBet))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // All-in
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700))
                    .clickable { onBetChange(actualMaxBet) },
                contentAlignment = Alignment.Center
            ) {
                Text("MAX", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Selector de manos (más compacto)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Manos: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            listOf(1, 2, 3, 4).forEach { hands ->
                val canAfford = minBet * hands <= playerChips
                val isSelected = numberOfHands == hands
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> Color(0xFF9B59B6)
                                canAfford -> Color(0xFF34495E)
                                else -> Color.Gray.copy(alpha = 0.2f)
                            }
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = Color(0xFFFFD700),
                            shape = CircleShape
                        )
                        .clickable(enabled = canAfford) { onHandsChange(hands) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hands",
                        color = if (canAfford) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
        
    }
}

@Composable
private fun PlayingControls(
    canHit: Boolean,
    canStand: Boolean,
    canDouble: Boolean,
    canSplit: Boolean,
    canSurrender: Boolean,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit,
    onSurrender: () -> Unit
) {
    // Una sola fila: [PEDIR grande] [PLANTARSE] [DOBLAR] [DIVIDIR] [RENDIRSE]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // PEDIR — botón principal, más grande
        Button(
            onClick = onHit,
            enabled = canHit,
            modifier = Modifier.weight(1.8f).height(64.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2ECC71),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "🎴 PEDIR",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (canHit) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }

        // Botones compactos — todos en la misma fila
        CompactPlayButton("✋", "PLANT.", canStand, Color(0xFFF39C12),
            Modifier.weight(1f).height(64.dp), onStand)
        CompactPlayButton("💰", "DOBL.", canDouble, Color(0xFF3498DB),
            Modifier.weight(1f).height(64.dp), onDouble)
        CompactPlayButton("✂️", "DIV.", canSplit, Color(0xFF9B59B6),
            Modifier.weight(1f).height(64.dp), onSplit)
        CompactPlayButton("🏳️", "REND.", canSurrender, Color(0xFFE74C3C),
            Modifier.weight(1f).height(64.dp), onSurrender)
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (small) 44.dp else 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text,
            fontSize = if (small) 12.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun CompactPlayButton(
    emoji: String,
    label: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 15.sp)
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResultControls(
    canContinue: Boolean,
    lastBet: Int,
    playerChips: Int,
    minBet: Int,
    maxBet: Int,
    onContinue: () -> Unit,
    onRepeatBet: () -> Unit,
    onLeaveTable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (canContinue) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Repetir apuesta
                if (lastBet in minBet..minOf(maxBet, playerChips)) {
                    Button(
                        onClick = onRepeatBet,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                    ) {
                        Text(
                            text = "🔄 REPETIR ($lastBet)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Cambiar apuesta
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("💰 CAMBIAR APUESTA", color = Color.White, fontSize = 12.sp)
                }
            }
        } else {
            Text(
                text = "💸 Te has quedado sin fichas",
                color = Color(0xFFE74C3C),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(onClick = onLeaveTable) {
            Text("🚪 Abandonar mesa", color = Color.White.copy(alpha = 0.6f))
        }
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
                .widthIn(max = 320.dp)
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
                Text(text = emoji, fontSize = 64.sp)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = text,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Puntuaciones
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tú", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(
                            "${result.playerFinalScore}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                    Text("vs", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Dealer", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(
                            "${result.dealerFinalScore}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pago
                Text(
                    text = when {
                        result.payout > 0 -> "+${result.payout} fichas"
                        result.payout < 0 -> "${result.payout} fichas"
                        else -> "±0 fichas"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = "Total: ${result.newChipsTotal}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Toca para continuar",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
