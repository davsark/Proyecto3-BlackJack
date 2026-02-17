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
 * Pantalla del menú principal
 */
@Composable
fun MainMenuScreen(
    onPlayPVE: () -> Unit,
    onPlayPVP: () -> Unit,
    onShowRecords: () -> Unit,
    onShowConfig: () -> Unit,
    onExit: () -> Unit
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "🎰",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "BLACKJACK",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700),
                letterSpacing = 4.sp
            )
            Text(
                text = "Multijugador",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Botones del menú
            MenuButton(
                text = "🎮 Nueva Partida PVE",
                subtitle = "Juega contra el Dealer",
                onClick = onPlayPVE,
                primary = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            MenuButton(
                text = "👥 Nueva Partida PVP",
                subtitle = "Compite contra otros jugadores",
                onClick = onPlayPVP,
                primary = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            MenuButton(
                text = "🏆 Ver Records",
                subtitle = "Estadísticas y mejores jugadores",
                onClick = onShowRecords
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            MenuButton(
                text = "⚙️ Configuración",
                subtitle = "Ajustes del juego",
                onClick = onShowConfig
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            TextButton(onClick = onExit) {
                Text(
                    text = "Salir",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    subtitle: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(320.dp)
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color(0xFF2ECC71) else Color(0xFF34495E)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
