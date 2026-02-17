package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.protocol.GameMode

@Composable
fun JoinGameScreen(
    onJoinGame: (playerName: String, gameMode: GameMode) -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(GameMode.PVE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎰 BLACKJACK",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unirse al Juego",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Nombre del Jugador") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Modo de Juego:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == GameMode.PVE,
                        onClick = { selectedMode = GameMode.PVE },
                        label = { Text("PVE (vs Dealer)") },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedMode == GameMode.PVP,
                        onClick = { selectedMode = GameMode.PVP },
                        label = { Text("PVP (Multijugador)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (playerName.isNotBlank()) {
                            onJoinGame(playerName.trim(), selectedMode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = playerName.isNotBlank()
                ) {
                    Text("Jugar")
                }
            }
        }
    }
}
