package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.project.network.GameClient
import org.example.project.protocol.*
import org.example.project.ui.TablePhase

/**
 * ViewModel que gestiona el estado del juego
 * 
 * FLUJO DE CASINO REAL:
 * 1. Conectar al servidor → Sentarse en mesa
 * 2. Fase BETTING: Colocar apuesta
 * 3. Fase PLAYING: Jugar la mano (Hit/Stand/Double/Split/Surrender)
 * 4. Fase DEALER_TURN: El dealer juega
 * 5. Fase RESULT: Ver resultado (breve, con opción de repetir apuesta)
 * 6. Volver a fase BETTING automáticamente
 * 7. Repetir hasta que el jugador decida irse o se quede sin fichas
 */
class GameViewModel : ViewModel() {
    private val gameClient = GameClient()

    // ═══════════════════════════════════════════════════════════════════════
    // ESTADO PRINCIPAL
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.MainMenu)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // ESTADO DE LA MESA (sesión continua)
    // ═══════════════════════════════════════════════════════════════════════
    
    // Fase actual de la mesa
    private val _tablePhase = MutableStateFlow(TablePhase.BETTING)
    val tablePhase: StateFlow<TablePhase> = _tablePhase.asStateFlow()
    
    // Fichas del jugador
    private val _playerChips = MutableStateFlow(1000)
    val playerChips: StateFlow<Int> = _playerChips.asStateFlow()
    
    // Apuesta actual de la mano en juego
    private val _currentBet = MutableStateFlow(0)
    val currentBet: StateFlow<Int> = _currentBet.asStateFlow()
    
    // Última apuesta realizada (para repetir)
    private val _lastBet = MutableStateFlow(0)
    val lastBet: StateFlow<Int> = _lastBet.asStateFlow()
    
    // Límites de apuesta
    private val _minBet = MutableStateFlow(10)
    val minBet: StateFlow<Int> = _minBet.asStateFlow()
    
    private val _maxBet = MutableStateFlow(500)
    val maxBet: StateFlow<Int> = _maxBet.asStateFlow()
    
    // Estado del juego actual (cartas, puntuaciones, acciones disponibles)
    private val _currentGameState = MutableStateFlow<ServerMessage.GameState?>(null)
    val currentGameState: StateFlow<ServerMessage.GameState?> = _currentGameState.asStateFlow()

    // Resultado de la última mano
    private val _gameResult = MutableStateFlow<ServerMessage.GameResult?>(null)
    val gameResult: StateFlow<ServerMessage.GameResult?> = _gameResult.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // OTROS ESTADOS
    // ═══════════════════════════════════════════════════════════════════════
    
    // Records
    private val _records = MutableStateFlow<List<Record>>(emptyList())
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    // Historial de manos de la sesión
    private val _handHistory = MutableStateFlow<List<HandHistory>>(emptyList())
    val handHistory: StateFlow<List<HandHistory>> = _handHistory.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // ESTADO PVP (mesa compartida)
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _pvpTableState = MutableStateFlow<ServerMessage.PvPTableState?>(null)
    val pvpTableState: StateFlow<ServerMessage.PvPTableState?> = _pvpTableState.asStateFlow()
    
    private val _isPvPMode = MutableStateFlow(false)
    val isPvPMode: StateFlow<Boolean> = _isPvPMode.asStateFlow()

    // ID del jugador
    private val _playerId = MutableStateFlow<String?>(null)
    
    // Configuración
    private val _numberOfDecks = MutableStateFlow(1)
    val numberOfDecks: StateFlow<Int> = _numberOfDecks.asStateFlow()

    private val _blackjackPayout = MutableStateFlow(1.5)
    val blackjackPayout: StateFlow<Double> = _blackjackPayout.asStateFlow()

    private val _dealerHitsOnSoft17 = MutableStateFlow(false)
    val dealerHitsOnSoft17: StateFlow<Boolean> = _dealerHitsOnSoft17.asStateFlow()

    private val _allowDoubleAfterSplit = MutableStateFlow(true)
    val allowDoubleAfterSplit: StateFlow<Boolean> = _allowDoubleAfterSplit.asStateFlow()

    private val _allowSurrender = MutableStateFlow(true)
    val allowSurrender: StateFlow<Boolean> = _allowSurrender.asStateFlow()

    private val _maxSplits = MutableStateFlow(3)
    val maxSplits: StateFlow<Int> = _maxSplits.asStateFlow()

    // Modo de juego y nombre
    private var selectedGameMode: GameMode = GameMode.PVE
    private var playerName: String = ""
    
    // Estadísticas de la sesión
    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    init {
        // Observar mensajes del servidor
        viewModelScope.launch {
            gameClient.serverMessages.collect { message ->
                message?.let { handleServerMessage(it) }
            }
        }

        // Observar errores de conexión
        viewModelScope.launch {
            gameClient.connectionError.collect { error ->
                error?.let {
                    _uiState.value = GameUiState.Error(it)
                }
            }
        }

        // Observar desconexión
        viewModelScope.launch {
            gameClient.isConnected.collect { connected ->
                if (!connected && _uiState.value !is GameUiState.MainMenu && _uiState.value !is GameUiState.Error) {
                    _uiState.value = GameUiState.MainMenu
                    resetTableState()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONEXIÓN Y ENTRADA A LA MESA
    // ═══════════════════════════════════════════════════════════════════════

    fun connect(host: String, port: Int) {
        viewModelScope.launch {
            _uiState.value = GameUiState.Connecting
            val success = gameClient.connect(host, port)
            if (success) {
                _uiState.value = GameUiState.Connected
            } else {
                _uiState.value = GameUiState.MainMenu
            }
        }
    }

    fun startPVE() {
        selectedGameMode = GameMode.PVE
        _uiState.value = GameUiState.Connecting
    }

    fun startPVP() {
        selectedGameMode = GameMode.PVP
        _uiState.value = GameUiState.Connecting
    }

    /**
     * Sentarse en la mesa (unirse al juego)
     */
    fun joinTable(playerName: String, gameMode: GameMode) {
        this.playerName = playerName
        this.selectedGameMode = gameMode
        
        viewModelScope.launch {
            val message = ClientMessage.JoinGame(playerName, gameMode, settings = buildGameSettings())
            gameClient.sendMessage(message)
            // Esperamos confirmación del servidor
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACCIONES DE APUESTA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Colocar apuesta y empezar nueva mano
     */
    fun placeBet(amount: Int, numberOfHands: Int = 1) {
        val totalBet = amount * numberOfHands
        
        // Validaciones
        if (totalBet > _playerChips.value) return
        if (amount < _minBet.value) return
        if (amount > _maxBet.value) return
        
        _currentBet.value = amount
        _lastBet.value = amount
        
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.PlaceBet(amount, numberOfHands))
            _tablePhase.value = TablePhase.PLAYING
        }
    }

    /**
     * Repetir última apuesta
     */
    fun repeatLastBet() {
        val last = _lastBet.value
        if (last in _minBet.value.._maxBet.value && last <= _playerChips.value) {
            placeBet(last, 1)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACCIONES DE JUEGO
    // ═══════════════════════════════════════════════════════════════════════

    fun hit() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.RequestCard)
        }
    }

    fun stand() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.Stand)
        }
    }

    fun double() {
        // Al doblar, se duplica la apuesta
        _currentBet.value = _currentBet.value * 2
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.Double)
        }
    }

    fun split() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.Split)
        }
    }

    fun surrender() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.Surrender)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTROL DE FLUJO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Continuar jugando (volver a fase de apuestas)
     */
    fun continuePlaying() {
        _gameResult.value = null
        _currentGameState.value = null
        _currentBet.value = 0
        _tablePhase.value = TablePhase.BETTING
        
        // Notificar al servidor que estamos listos para nueva mano
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.NewGame)
        }
    }

    /**
     * Abandonar la mesa
     */
    fun leaveTable() {
        gameClient.disconnect()
        resetTableState()
        _uiState.value = GameUiState.MainMenu
    }

    /**
     * Reset del estado de la mesa
     */
    private fun resetTableState() {
        _tablePhase.value = TablePhase.BETTING
        _playerChips.value = 1000
        _currentBet.value = 0
        _lastBet.value = 0
        _currentGameState.value = null
        _gameResult.value = null
        _handHistory.value = emptyList()
        _sessionStats.value = SessionStats()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OTROS
    // ═══════════════════════════════════════════════════════════════════════

    fun requestRecords() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.RequestRecords)
        }
    }

    fun requestHistory() {
        viewModelScope.launch {
            gameClient.sendMessage(ClientMessage.RequestHistory)
        }
    }

    fun showConfig() {
        _uiState.value = GameUiState.ShowingConfig
    }

    fun setNumberOfDecks(decks: Int) { _numberOfDecks.value = decks }
    fun setBlackjackPayout(payout: Double) { _blackjackPayout.value = payout }
    fun setDealerHitsOnSoft17(value: Boolean) { _dealerHitsOnSoft17.value = value }
    fun setAllowDoubleAfterSplit(value: Boolean) { _allowDoubleAfterSplit.value = value }
    fun setAllowSurrender(value: Boolean) { _allowSurrender.value = value }
    fun setMaxSplits(value: Int) { _maxSplits.value = value }

    private fun buildGameSettings() = GameSettings(
        numberOfDecks = _numberOfDecks.value,
        blackjackPayout = _blackjackPayout.value,
        dealerHitsOnSoft17 = _dealerHitsOnSoft17.value,
        allowDoubleAfterSplit = _allowDoubleAfterSplit.value,
        allowSurrender = _allowSurrender.value,
        maxSplits = _maxSplits.value
    )

    fun backToGame() {
        _uiState.value = GameUiState.AtTable
    }

    fun backToMenu() {
        _uiState.value = GameUiState.MainMenu
    }

    fun clearError() {
        if (_uiState.value is GameUiState.Error) {
            _uiState.value = if (gameClient.isConnected.value) {
                GameUiState.AtTable
            } else {
                GameUiState.MainMenu
            }
        }
        gameClient.clearError()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MANEJO DE MENSAJES DEL SERVIDOR
    // ═══════════════════════════════════════════════════════════════════════

    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.JoinConfirmation -> {
                _playerId.value = message.playerId
                _playerChips.value = message.initialChips
                _tablePhase.value = TablePhase.BETTING
                _uiState.value = GameUiState.AtTable
                println("✅ ${message.message}")
            }

            is ServerMessage.TableState -> {
                _playerChips.value = message.currentPlayerChips
                _minBet.value = message.minBet
                _maxBet.value = message.maxBet
            }

            is ServerMessage.RequestBet -> {
                _playerChips.value = message.currentChips
                _minBet.value = message.minBet
                _maxBet.value = message.maxBet
                _tablePhase.value = TablePhase.BETTING
                _currentBet.value = 0
                _gameResult.value = null
            }

            is ServerMessage.GameState -> {
                _currentGameState.value = message
                _playerChips.value = message.playerChips
                _currentBet.value = message.currentBet
                
                // Determinar fase según el estado
                _tablePhase.value = when (message.gameState) {
                    GamePhase.PLAYER_TURN -> TablePhase.PLAYING
                    GamePhase.DEALER_TURN -> TablePhase.DEALER_TURN
                    GamePhase.GAME_OVER -> TablePhase.RESULT
                    else -> _tablePhase.value
                }
                
                // Asegurar que estamos en la pantalla de mesa
                if (_uiState.value != GameUiState.AtTable) {
                    _uiState.value = GameUiState.AtTable
                }
            }

            is ServerMessage.GameResult -> {
                _gameResult.value = message
                _playerChips.value = message.newChipsTotal
                _tablePhase.value = TablePhase.RESULT
                
                // Actualizar estadísticas de sesión
                updateSessionStats(message)
                
                // Agregar al historial local
                addToLocalHistory(message)
            }

            is ServerMessage.RecordsList -> {
                _records.value = message.records
                _uiState.value = GameUiState.ShowingRecords
            }

            is ServerMessage.HandHistoryList -> {
                _handHistory.value = message.history
                _uiState.value = GameUiState.ShowingHistory
            }

            is ServerMessage.Error -> {
                // Mostrar error pero no salir de la mesa necesariamente
                println("❌ Error del servidor: ${message.errorMessage}")
                // Solo cambiar estado si es error crítico
                if (message.errorMessage.contains("sin fichas", ignoreCase = true)) {
                    // Sin fichas - permitir que vean el mensaje pero no pueden continuar
                }
            }

            is ServerMessage.Pong -> {
                // Ignorar
            }
            
            is ServerMessage.PvPTableState -> {
                // Estado de mesa PvP
                _pvpTableState.value = message
                _isPvPMode.value = true
                
                // Actualizar fichas del jugador actual
                val myInfo = message.players.find { it.playerId == message.currentPlayerId }
                if (myInfo != null) {
                    _playerChips.value = myInfo.chips
                    _currentBet.value = myInfo.currentBet
                }
                
                // Determinar fase de la mesa
                _tablePhase.value = when (message.phase) {
                    "BETTING" -> TablePhase.BETTING
                    "DEALING" -> TablePhase.PLAYING
                    "PLAYER_TURNS" -> TablePhase.PLAYING
                    "DEALER_TURN" -> TablePhase.DEALER_TURN
                    "RESOLVING", "ROUND_END" -> TablePhase.RESULT
                    else -> TablePhase.BETTING
                }
                
                // Asegurar que estamos en la pantalla correcta
                if (_uiState.value != GameUiState.AtPvPTable) {
                    _uiState.value = GameUiState.AtPvPTable
                }
                
                println("🎰 Mesa PvP actualizada: ${message.players.size} jugadores, fase: ${message.phase}")
            }
        }
        gameClient.clearLastMessage()
    }

    /**
     * Actualiza las estadísticas de la sesión
     */
    private fun updateSessionStats(result: ServerMessage.GameResult) {
        val current = _sessionStats.value
        _sessionStats.value = current.copy(
            handsPlayed = current.handsPlayed + 1,
            handsWon = current.handsWon + if (result.result == GameResultType.WIN || result.result == GameResultType.BLACKJACK) 1 else 0,
            handsLost = current.handsLost + if (result.result == GameResultType.LOSE) 1 else 0,
            blackjacks = current.blackjacks + if (result.result == GameResultType.BLACKJACK) 1 else 0,
            totalProfit = current.totalProfit + result.payout,
            biggestWin = maxOf(current.biggestWin, if (result.payout > 0) result.payout else 0),
            biggestLoss = minOf(current.biggestLoss, if (result.payout < 0) result.payout else 0)
        )
    }

    /**
     * Agrega la mano al historial local
     */
    private fun addToLocalHistory(result: ServerMessage.GameResult) {
        val currentState = _currentGameState.value ?: return
        
        val historyEntry = HandHistory(
            playerHand = currentState.playerHand,
            dealerHand = result.dealerFinalHand,
            result = result.result,
            bet = _currentBet.value,
            payout = result.payout,
            timestamp = System.currentTimeMillis(),
            playerScore = result.playerFinalScore,
            dealerScore = result.dealerFinalScore
        )
        
        // Mantener solo las últimas 10
        val newHistory = listOf(historyEntry) + _handHistory.value.take(9)
        _handHistory.value = newHistory
    }

    override fun onCleared() {
        super.onCleared()
        gameClient.disconnect()
    }
}

/**
 * Estados de la UI
 */
sealed class GameUiState {
    data object MainMenu : GameUiState()
    data object ShowingConfig : GameUiState()
    data object Connecting : GameUiState()
    data object Connected : GameUiState()
    data object AtTable : GameUiState()       // Estado PVE: en la mesa jugando
    data object AtPvPTable : GameUiState()    // Estado PVP: mesa compartida
    data object ShowingRecords : GameUiState()
    data object ShowingHistory : GameUiState()
    data class Error(val message: String) : GameUiState()
}

/**
 * Estadísticas de la sesión actual
 */
data class SessionStats(
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val handsLost: Int = 0,
    val blackjacks: Int = 0,
    val totalProfit: Int = 0,
    val biggestWin: Int = 0,
    val biggestLoss: Int = 0
) {
    val winRate: Double
        get() = if (handsPlayed > 0) handsWon.toDouble() / handsPlayed else 0.0
}
