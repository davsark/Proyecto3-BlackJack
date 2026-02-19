package org.example.project.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.protocol.Card
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import blackjack.composeapp.generated.resources.Res
import blackjack.composeapp.generated.resources.*

/**
 * Vista de una carta para mostrar en el juego
 */
@Composable
fun CardImage(
    card: Card,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 125.dp,
    cardHeight: Dp = 175.dp
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val drawableRes = getCardResource(card)
            if (drawableRes != null) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = if (card.hidden) "Carta oculta" else "${card.rank.name} de ${card.suit.displayName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback con símbolos de texto
                CardTextFallback(card)
            }
        }
    }
}

@Composable
private fun CardTextFallback(card: Card) {
    if (card.hidden) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1565C0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🂠",
                fontSize = 40.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.rank.symbol,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = when (card.suit.displayName) {
                    "Corazones", "Diamantes" -> Color.Red
                    else -> Color.Black
                }
            )
            Text(
                text = card.suit.symbol,
                fontSize = 20.sp,
                color = when (card.suit.displayName) {
                    "Corazones", "Diamantes" -> Color.Red
                    else -> Color.Black
                }
            )
        }
    }
}

private fun getCardResource(card: Card): DrawableResource? {
    if (card.hidden) {
        return Res.drawable.card_back
    }
    
    val imageName = card.getImageName()
    
    return when (imageName) {
        "card_hearts_A" -> Res.drawable.card_hearts_A
        "card_hearts_02" -> Res.drawable.card_hearts_02
        "card_hearts_03" -> Res.drawable.card_hearts_03
        "card_hearts_04" -> Res.drawable.card_hearts_04
        "card_hearts_05" -> Res.drawable.card_hearts_05
        "card_hearts_06" -> Res.drawable.card_hearts_06
        "card_hearts_07" -> Res.drawable.card_hearts_07
        "card_hearts_08" -> Res.drawable.card_hearts_08
        "card_hearts_09" -> Res.drawable.card_hearts_09
        "card_hearts_10" -> Res.drawable.card_hearts_10
        "card_hearts_J" -> Res.drawable.card_hearts_J
        "card_hearts_Q" -> Res.drawable.card_hearts_Q
        "card_hearts_K" -> Res.drawable.card_hearts_K
        "card_diamonds_A" -> Res.drawable.card_diamonds_A
        "card_diamonds_02" -> Res.drawable.card_diamonds_02
        "card_diamonds_03" -> Res.drawable.card_diamonds_03
        "card_diamonds_04" -> Res.drawable.card_diamonds_04
        "card_diamonds_05" -> Res.drawable.card_diamonds_05
        "card_diamonds_06" -> Res.drawable.card_diamonds_06
        "card_diamonds_07" -> Res.drawable.card_diamonds_07
        "card_diamonds_08" -> Res.drawable.card_diamonds_08
        "card_diamonds_09" -> Res.drawable.card_diamonds_09
        "card_diamonds_10" -> Res.drawable.card_diamonds_10
        "card_diamonds_J" -> Res.drawable.card_diamonds_J
        "card_diamonds_Q" -> Res.drawable.card_diamonds_Q
        "card_diamonds_K" -> Res.drawable.card_diamonds_K
        "card_clubs_A" -> Res.drawable.card_clubs_A
        "card_clubs_02" -> Res.drawable.card_clubs_02
        "card_clubs_03" -> Res.drawable.card_clubs_03
        "card_clubs_04" -> Res.drawable.card_clubs_04
        "card_clubs_05" -> Res.drawable.card_clubs_05
        "card_clubs_06" -> Res.drawable.card_clubs_06
        "card_clubs_07" -> Res.drawable.card_clubs_07
        "card_clubs_08" -> Res.drawable.card_clubs_08
        "card_clubs_09" -> Res.drawable.card_clubs_09
        "card_clubs_10" -> Res.drawable.card_clubs_10
        "card_clubs_J" -> Res.drawable.card_clubs_J
        "card_clubs_Q" -> Res.drawable.card_clubs_Q
        "card_clubs_K" -> Res.drawable.card_clubs_K
        "card_spades_A" -> Res.drawable.card_spades_A
        "card_spades_02" -> Res.drawable.card_spades_02
        "card_spades_03" -> Res.drawable.card_spades_03
        "card_spades_04" -> Res.drawable.card_spades_04
        "card_spades_05" -> Res.drawable.card_spades_05
        "card_spades_06" -> Res.drawable.card_spades_06
        "card_spades_07" -> Res.drawable.card_spades_07
        "card_spades_08" -> Res.drawable.card_spades_08
        "card_spades_09" -> Res.drawable.card_spades_09
        "card_spades_10" -> Res.drawable.card_spades_10
        "card_spades_J" -> Res.drawable.card_spades_J
        "card_spades_Q" -> Res.drawable.card_spades_Q
        "card_spades_K" -> Res.drawable.card_spades_K
        else -> null
    }
}
