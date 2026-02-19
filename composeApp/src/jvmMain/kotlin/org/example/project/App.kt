package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.ui.*
import org.example.project.viewmodel.GameUiState
import org.example.project.viewmodel.GameViewModel
import kotlin.system.exitProcess

/**
 * Aplicación principal de Blackjack
 * 
 * FLUJO SIMPLIFICADO (estilo casino real):
 * 
 * 1. MainMenu → Seleccionar modo (PVE/PVP)
 * 2. Connecting → Conectar al servidor
 * 3. Connected → Introducir nombre y sentarse en mesa
 * 4. AtTable → PANTALLA PRINCIPAL con flujo continuo:
 *    - Fase BETTING: Elegir apuesta
 *    - Fase PLAYING: Jugar la mano
 *    - Fase RESULT: Ver resultado → Repetir apuesta o cambiar
 *    - (El jugador decide cuándo irse)
 * 5. ShowingRecords/ShowingHistory → Ver estadísticas
 */
@Composable
fun App() {
    MaterialTheme {
        val viewModel: GameViewModel = viewModel { GameViewModel() }
        
        // Estados principales
        val uiState by viewModel.uiState.collectAsState()
        val numberOfDecks by viewModel.numberOfDecks.collectAsState()

        // Configuración de reglas
        val blackjackPayout by viewModel.blackjackPayout.collectAsState()
        val dealerHitsOnSoft17 by viewModel.dealerHitsOnSoft17.collectAsState()
        val allowDoubleAfterSplit by viewModel.allowDoubleAfterSplit.collectAsState()
        val allowSurrender by viewModel.allowSurrender.collectAsState()
        val maxSplits by viewModel.maxSplits.collectAsState()
        
        // Estados de la mesa
        val tablePhase by viewModel.tablePhase.collectAsState()
        val playerChips by viewModel.playerChips.collectAsState()
        val currentBet by viewModel.currentBet.collectAsState()
        val lastBet by viewModel.lastBet.collectAsState()
        val minBet by viewModel.minBet.collectAsState()
        val maxBet by viewModel.maxBet.collectAsState()
        val gameState by viewModel.currentGameState.collectAsState()
        val gameResult by viewModel.gameResult.collectAsState()
        
        // Otros estados
        val records by viewModel.records.collectAsState()
        val handHistory by viewModel.handHistory.collectAsState()

        when (uiState) {
            // ═══════════════════════════════════════════════════════════════
            // MENÚ PRINCIPAL
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.MainMenu -> {
                MainMenuScreen(
                    onPlayPVE = { viewModel.startPVE() },
                    onPlayPVP = { viewModel.startPVP() },
                    onShowRecords = { 
                        // Conectar para ver records
                        viewModel.connect("localhost", 9999)
                    },
                    onShowConfig = { viewModel.showConfig() },
                    onExit = { exitProcess(0) }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // CONFIGURACIÓN
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.ShowingConfig -> {
                ConfigScreen(
                    currentDecks = numberOfDecks,
                    onDecksChange = { viewModel.setNumberOfDecks(it) },
                    currentBlackjackPayout = blackjackPayout,
                    onBlackjackPayoutChange = { viewModel.setBlackjackPayout(it) },
                    currentDealerHitsOnSoft17 = dealerHitsOnSoft17,
                    onDealerHitsOnSoft17Change = { viewModel.setDealerHitsOnSoft17(it) },
                    currentAllowDoubleAfterSplit = allowDoubleAfterSplit,
                    onAllowDoubleAfterSplitChange = { viewModel.setAllowDoubleAfterSplit(it) },
                    currentAllowSurrender = allowSurrender,
                    onAllowSurrenderChange = { viewModel.setAllowSurrender(it) },
                    currentMaxSplits = maxSplits,
                    onMaxSplitsChange = { viewModel.setMaxSplits(it) },
                    onBack = { viewModel.backToMenu() }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // CONEXIÓN
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.Connecting -> {
                ConnectionScreen(
                    onConnect = { host, port ->
                        viewModel.connect(host, port)
                    }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // SENTARSE EN LA MESA (introducir nombre)
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.Connected -> {
                JoinGameScreen(
                    onJoinGame = { playerName, gameMode ->
                        viewModel.joinTable(playerName, gameMode)
                    }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // MESA DE JUEGO PVE - Pantalla principal unificada
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.AtTable -> {
                TableScreen(
                    // Estado
                    tablePhase = tablePhase,
                    playerChips = playerChips,
                    currentBet = currentBet,
                    lastBet = lastBet,
                    minBet = minBet,
                    maxBet = maxBet,
                    gameState = gameState,
                    gameResult = gameResult,
                    
                    // Acciones de apuesta
                    onPlaceBet = { amount, hands -> viewModel.placeBet(amount, hands) },
                    onRepeatLastBet = { viewModel.repeatLastBet() },
                    
                    // Acciones de juego
                    onHit = { viewModel.hit() },
                    onStand = { viewModel.stand() },
                    onDouble = { viewModel.double() },
                    onSplit = { viewModel.split() },
                    onSurrender = { viewModel.surrender() },
                    
                    // Navegación
                    onContinuePlaying = { viewModel.continuePlaying() },
                    onShowRecords = { viewModel.requestRecords() },
                    onShowHistory = { viewModel.requestHistory() },
                    onLeaveTable = { viewModel.leaveTable() }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // MESA PVP - Múltiples jugadores en la misma mesa
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.AtPvPTable -> {
                val pvpTableState by viewModel.pvpTableState.collectAsState()
                
                pvpTableState?.let { tableState ->
                    PvPTableScreen(
                        tableState = tableState,
                        playerChips = playerChips,
                        currentBet = currentBet,
                        minBet = minBet,
                        maxBet = maxBet,
                        gameResult = gameResult,
                        
                        // Acciones
                        onPlaceBet = { amount -> viewModel.placeBet(amount, 1) },
                        onHit = { viewModel.hit() },
                        onStand = { viewModel.stand() },
                        onDouble = { viewModel.double() },
                        onSurrender = { viewModel.surrender() },
                        onContinuePlaying = { viewModel.continuePlaying() },
                        onShowRecords = { viewModel.requestRecords() },
                        onLeaveTable = { viewModel.leaveTable() }
                    )
                } ?: run {
                    // Esperando estado de mesa
                    ConnectionScreen(
                        onConnect = { _, _ -> }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // RECORDS
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.ShowingRecords -> {
                RecordsScreen(
                    records = records,
                    onBack = { viewModel.backToGame() }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // HISTORIAL DE MANOS
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.ShowingHistory -> {
                HistoryScreen(
                    history = handHistory,
                    onBack = { viewModel.backToGame() }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // ERROR
            // ═══════════════════════════════════════════════════════════════
            is GameUiState.Error -> {
                ErrorScreen(
                    message = (uiState as GameUiState.Error).message,
                    onDismiss = { viewModel.clearError() },
                    onDisconnect = { viewModel.leaveTable() }
                )
            }
        }
    }
}
