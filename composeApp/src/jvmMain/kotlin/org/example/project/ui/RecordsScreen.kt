package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.protocol.Record

@Composable
fun RecordsScreen(
    records: List<Record>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏆 Top Records",
                style = MaterialTheme.typography.displaySmall
            )

            Button(onClick = onBack) {
                Text("Volver")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (records.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No hay records todavía",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Encabezado
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Puesto",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = "Jugador",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "V",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                text = "D",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                text = "BJ",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                text = "% Victorias",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }

                // Registros
                itemsIndexed(records) { index, record ->
                    RecordItem(
                        position = index + 1,
                        record = record
                    )
                }
            }
        }
    }
}

@Composable
fun RecordItem(
    position: Int,
    record: Record
) {
    val medalEmoji = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$position."
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = medalEmoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(60.dp)
            )

            Text(
                text = record.playerName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${record.wins}",
                modifier = Modifier.width(40.dp)
            )

            Text(
                text = "${record.losses}",
                modifier = Modifier.width(40.dp)
            )

            Text(
                text = "${record.blackjacks}",
                modifier = Modifier.width(40.dp)
            )

            val winRate = (record.winRate * 100).toInt()
            Text(
                text = "$winRate%",
                fontWeight = if (winRate >= 50) FontWeight.Bold else FontWeight.Normal,
                color = if (winRate >= 50) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.width(100.dp)
            )
        }
    }
}
