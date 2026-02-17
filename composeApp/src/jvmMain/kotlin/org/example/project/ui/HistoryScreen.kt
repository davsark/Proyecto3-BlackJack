package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import org.example.project.protocol.GameResultType
import org.example.project.protocol.HandHistory
import org.example.project.ui.common.CardImage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla de historial de las últimas 10 manos jugadas
 */
@Composable
fun HistoryScreen(
    history: List<HandHistory>,
    onBack: () -> Unit
) {
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
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📜 Historial de Manos",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34495E))
                ) {
                    Text("Volver")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Últimas ${history.size} manos jugadas",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (history.isEmpty()) {
                // Sin historial
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🃏",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No hay manos registradas",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "¡Juega algunas partidas para ver tu historial!",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // Lista de manos
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(history) { index, hand ->
                        HistoryHandCard(
                            handNumber = index + 1,
                            hand = hand
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHandCard(
    handNumber: Int,
    hand: HandHistory
) {
    val resultColor = when (hand.result) {
        GameResultType.WIN, GameResultType.BLACKJACK -> Color(0xFF2ECC71)
        GameResultType.LOSE -> Color(0xFFE74C3C)
        GameResultType.PUSH -> Color(0xFFF39C12)
        GameResultType.SURRENDER -> Color(0xFF9B59B6)
    }
    
    val resultEmoji = when (hand.result) {
        GameResultType.WIN -> "✅"
        GameResultType.BLACKJACK -> "🎰"
        GameResultType.LOSE -> "❌"
        GameResultType.PUSH -> "🤝"
        GameResultType.SURRENDER -> "🏳️"
    }
    
    val resultText = when (hand.result) {
        GameResultType.WIN -> "Victoria"
        GameResultType.BLACKJACK -> "¡Blackjack!"
        GameResultType.LOSE -> "Derrota"
        GameResultType.PUSH -> "Empate"
        GameResultType.SURRENDER -> "Rendición"
    }
    
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = dateFormat.format(Date(hand.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header de la mano
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "#$handNumber",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$resultEmoji $resultText",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = resultColor
                    )
                }
                
                Text(
                    text = timeString,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cartas del jugador
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tu mano:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.width(70.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-15).dp)
                ) {
                    hand.playerHand.forEach { card ->
                        CardImage(
                            card = card,
                            cardWidth = 40.dp,
                            cardHeight = 56.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "(${hand.playerScore})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cartas del dealer
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dealer:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.width(70.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-15).dp)
                ) {
                    hand.dealerHand.forEach { card ->
                        CardImage(
                            card = card,
                            cardWidth = 40.dp,
                            cardHeight = 56.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "(${hand.dealerScore})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Información de apuesta/pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Apuesta: ${hand.bet}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = when {
                        hand.payout > 0 -> "+${hand.payout} fichas"
                        hand.payout < 0 -> "${hand.payout} fichas"
                        else -> "±0 fichas"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        hand.payout > 0 -> Color(0xFF2ECC71)
                        hand.payout < 0 -> Color(0xFFE74C3C)
                        else -> Color(0xFFF39C12)
                    }
                )
            }
        }
    }
}
