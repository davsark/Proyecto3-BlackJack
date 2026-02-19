package org.example.project.server

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.config.GameConfig
import org.example.project.model.Deck
import org.example.project.protocol.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.*

/**
 * Maneja la comunicación con un cliente individual
 * 
 * En modo PVE: Juega contra el dealer local
 * En modo PVP: Se une a una mesa compartida con otros jugadores
 */
class ClientHandler(
    private val socket: Socket,
    private val recordsManager: RecordsManager,
    private val gameSettings: GameSettings,
    private val tableManager: TableManager
) {
    private val playerId = UUID.randomUUID().toString()
    private var playerName: String = "Jugador"
    private lateinit var input: BufferedReader
    private lateinit var output: BufferedWriter
    private val json = Json { ignoreUnknownKeys = true }

    // Estado del juego (PVE)
    private val deck = Deck(gameSettings.numberOfDecks)
    private lateinit var dealerAI: DealerAI
    private var gameMode: GameMode? = null
    
    // Mesa PvP (cuando está en modo PVP)
    private var currentTable: Table? = null
    
    // Sistema de fichas y apuestas
    private var playerChips: Int = gameSettings.initialChips
    private var currentBet: Int = 0
    private var isInGame: Boolean = false
    
    // Soporte para múltiples manos
    private var numberOfHands: Int = 1
    private var totalBet: Int = 0
    
    // Historial de manos (últimas 10)
    private val handHistory = mutableListOf<HandHistory>()

    init {
        deck.shuffle()
    }

    /**
     * Maneja la conexión del cliente
     */
    suspend fun handle() = coroutineScope {
        try {
            // Configurar streams
            input = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            // Configurar timeout
            socket.soTimeout = GameConfig.CONNECTION_TIMEOUT_MS.toInt()

            println("✅ Cliente conectado: ${socket.inetAddress.hostAddress}:${socket.port}")

            // Loop principal de mensajes
            while (socket.isConnected && !socket.isClosed) {
                val line = try {
                    input.readLine()
                } catch (e: SocketTimeoutException) {
                    println("⏱️ Timeout del cliente $playerName")
                    break
                } catch (e: SocketException) {
                    println("🔌 Conexión cerrada: $playerName")
                    break
                }

                if (line == null) {
                    println("👋 Cliente desconectado: $playerName")
                    break
                }

                try {
                    val message = json.decodeFromString<ClientMessage>(line)
                    handleMessage(message)
                } catch (e: Exception) {
                    println("⚠️ Error al procesar mensaje de $playerName: ${e.message}")
                    sendError("Error al procesar mensaje: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("❌ Error en ClientHandler para $playerName: ${e.message}")
        } finally {
            // Limpieza PvP
            if (gameMode == GameMode.PVP) {
                tableManager.removePlayer(playerId)
            }
            cleanup()
        }
    }

    /**
     * Procesa un mensaje del cliente
     */
    private suspend fun handleMessage(message: ClientMessage) {
        when (message) {
            is ClientMessage.JoinGame -> handleJoinGame(message)
            is ClientMessage.PlaceBet -> handlePlaceBet(message)
            is ClientMessage.RequestCard -> handleRequestCard()
            is ClientMessage.Stand -> handleStand()
            is ClientMessage.Double -> handleDouble()
            is ClientMessage.Split -> handleSplit()
            is ClientMessage.Surrender -> handleSurrender()
            is ClientMessage.NewGame -> handleNewGame()
            is ClientMessage.RequestRecords -> handleRequestRecords()
            is ClientMessage.RequestHistory -> handleRequestHistory()
            is ClientMessage.SelectHand -> handleSelectHand(message)
            is ClientMessage.Ping -> handlePing()
        }
    }

    /**
     * Maneja la unión de un jugador al juego
     */
    private suspend fun handleJoinGame(message: ClientMessage.JoinGame) {
        playerName = message.playerName
        gameMode = message.gameMode
        playerChips = message.buyIn.coerceIn(0, gameSettings.initialChips)

        println("👤 $playerName se une (Modo: ${message.gameMode}, Fichas: $playerChips)")

        when (message.gameMode) {
            GameMode.PVE -> {
                // Modo solitario — usar settings del cliente si las envió
                val effectiveSettings = message.settings?.let { s ->
                    gameSettings.copy(
                        numberOfDecks      = s.numberOfDecks,
                        blackjackPayout    = s.blackjackPayout,
                        dealerHitsOnSoft17 = s.dealerHitsOnSoft17,
                        allowDoubleAfterSplit = s.allowDoubleAfterSplit,
                        allowSurrender     = s.allowSurrender,
                        maxSplits          = s.maxSplits
                    )
                } ?: gameSettings
                dealerAI = DealerAI(deck, effectiveSettings)
                sendMessage(ServerMessage.JoinConfirmation(
                    playerId = playerId,
                    message = "Bienvenido $playerName. Modo: Jugador vs Dealer",
                    initialChips = playerChips
                ))
                sendMessage(ServerMessage.TableState(
                    players = listOf(PlayerInfo(playerName, 0, 0, false, playerChips, 0)),
                    minBet = gameSettings.minBet,
                    maxBet = gameSettings.maxBet,
                    currentPlayerChips = playerChips
                ))
                sendMessage(ServerMessage.RequestBet(
                    minBet = gameSettings.minBet,
                    maxBet = minOf(gameSettings.maxBet, playerChips),
                    currentChips = playerChips
                ))
            }
            GameMode.PVP -> {
                // Modo multijugador - unirse a mesa compartida
                sendMessage(ServerMessage.JoinConfirmation(
                    playerId = playerId,
                    message = "Bienvenido $playerName. Buscando mesa PvP...",
                    initialChips = playerChips
                ))
                
                // Buscar o crear mesa
                val table = tableManager.findOrCreateTable(
                    playerId = playerId,
                    playerName = playerName,
                    chips = playerChips,
                    callback = { message -> sendMessage(message) }
                )
                
                if (table != null) {
                    currentTable = table
                    println("🎰 $playerName unido a mesa PvP: ${table.tableId}")
                    // El estado de la mesa se enviará automáticamente desde Table
                } else {
                    sendError("No se pudo unir a una mesa. Intenta de nuevo.")
                }
            }
        }
        // Records se solicitan manualmente cuando el jugador lo pida
    }

    /**
     * Maneja la apuesta del jugador (soporta múltiples manos en PVE, simple en PVP)
     */
    private suspend fun handlePlaceBet(message: ClientMessage.PlaceBet) {
        // En modo PVP, usar la mesa compartida
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.placeBet(playerId, message.amount)
            return
        }
        
        // Modo PVE - lógica existente
        val betAmount = message.amount
        val numHands = message.numberOfHands.coerceIn(1, 3)
        val totalBetRequired = betAmount * numHands
        
        // Validar apuesta
        if (betAmount < gameSettings.minBet) {
            sendError("La apuesta mínima es ${gameSettings.minBet}")
            return
        }
        if (totalBetRequired > playerChips) {
            sendError("No tienes suficientes fichas. Necesitas: $totalBetRequired, Tienes: $playerChips")
            return
        }
        if (betAmount > gameSettings.maxBet) {
            sendError("La apuesta máxima es ${gameSettings.maxBet}")
            return
        }
        
        currentBet = betAmount
        numberOfHands = numHands
        totalBet = totalBetRequired
        isInGame = true
        
        println("💰 $playerName apuesta $currentBet fichas x $numberOfHands manos = $totalBet total")
        
        // Iniciar la partida
        startGame()
    }

    /**
     * Inicia una nueva partida después de la apuesta (soporta múltiples manos)
     */
    private suspend fun startGame() {
        dealerAI.checkAndResetDeck()
        val gameState = dealerAI.startNewGame(playerId, currentBet, playerChips - totalBet, numberOfHands)
        sendMessage(gameState)

        // Verificar si hay Blackjack natural (solo en modo de una mano)
        if (numberOfHands == 1 && gameState.playerScore == 21 && gameState.playerHand.size == 2) {
            delay(500)
            finishGame()
        }
    }

    /**
     * Maneja la petición de carta
     */
    private suspend fun handleRequestCard() {
        // En modo PVP, usar la mesa compartida
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerHit(playerId)
            return
        }
        
        // Modo PVE
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        val gameState = dealerAI.playerHit(playerId)
        sendMessage(gameState)

        if (gameState.gameState == GamePhase.GAME_OVER) {
            delay(500)
            finishGame()
        }
    }

    /**
     * Maneja cuando el jugador se planta
     */
    private suspend fun handleStand() {
        // En modo PVP, usar la mesa compartida
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerStand(playerId)
            return
        }
        
        // Modo PVE
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        val gameState = dealerAI.playerStand(playerId)
        sendMessage(gameState)

        // Solo finalizar si el juego terminó (todas las manos completas + dealer jugó)
        if (gameState.gameState == GamePhase.GAME_OVER) {
            delay(500)
            finishGame()
        }
    }

    /**
     * Maneja cuando el jugador dobla
     */
    private suspend fun handleDouble() {
        // En modo PVP, usar la mesa compartida
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerDouble(playerId)
            return
        }
        
        // Modo PVE
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }
        
        // Verificar que puede doblar
        if (currentBet > playerChips - currentBet) {
            sendError("No tienes suficientes fichas para doblar")
            return
        }

        val result = dealerAI.playerDouble(playerId)
        if (result == null) {
            sendError("No puedes doblar en este momento")
            return
        }
        
        println("🎲 $playerName dobla")
        
        sendMessage(result)
        
        // Solo finalizar si el juego terminó
        if (result.gameState == GamePhase.GAME_OVER) {
            delay(500)
            finishGame()
        }
    }

    /**
     * Maneja cuando el jugador divide
     */
    private suspend fun handleSplit() {
        // Split no disponible en PVP por simplicidad
        if (gameMode == GameMode.PVP) {
            sendError("Split no disponible en modo PvP")
            return
        }
        
        // Modo PVE
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }
        
        // Verificar que puede dividir
        if (currentBet > playerChips - currentBet) {
            sendError("No tienes suficientes fichas para dividir")
            return
        }

        val result = dealerAI.playerSplit(playerId)
        if (result == null) {
            sendError("No puedes dividir en este momento (necesitas dos cartas del mismo valor)")
            return
        }
        
        println("✂️ $playerName divide su mano")
        sendMessage(result)
    }

    /**
     * Maneja cuando el jugador se rinde
     */
    private suspend fun handleSurrender() {
        // En modo PVP, usar la mesa compartida
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerSurrender(playerId)
            return
        }
        
        // Modo PVE
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        if (!gameSettings.allowSurrender) {
            sendError("La rendición no está permitida en esta mesa")
            return
        }

        val result = dealerAI.playerSurrender(playerId)
        if (result == null) {
            sendError("No puedes rendirte en este momento")
            return
        }
        
        // Devolver mitad de la apuesta
        val refund = currentBet / 2
        playerChips += refund
        isInGame = false
        
        println("🏳️ $playerName se rinde. Recupera $refund fichas")
        sendMessage(result)
        
        // Solicitar nueva apuesta si tiene fichas
        if (playerChips >= gameSettings.minBet) {
            sendMessage(ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, playerChips),
                currentChips = playerChips
            ))
        }
    }

    /**
     * Finaliza el juego y envía el resultado
     */
    private suspend fun finishGame() {
        val result = dealerAI.getGameResult(playerId, currentBet, playerChips - totalBet)
        
        // Actualizar fichas
        playerChips = result.newChipsTotal
        isInGame = false
        
        println("🏆 Resultado para $playerName: ${result.result} | Pago: ${result.payout} | Fichas: $playerChips")
        
        sendMessage(result)
        
        // Guardar en historial (últimas 10 manos)
        val historyEntry = HandHistory(
            playerHand = dealerAI.getPlayerHand(playerId),
            dealerHand = result.dealerFinalHand,
            result = result.result,
            bet = totalBet,
            payout = result.payout,
            timestamp = System.currentTimeMillis(),
            playerScore = result.playerFinalScore,
            dealerScore = result.dealerFinalScore
        )
        handHistory.add(0, historyEntry)
        if (handHistory.size > 10) {
            handHistory.removeAt(handHistory.size - 1)
        }

        // Guardar en records
        recordsManager.recordGameResult(
            playerName = playerName,
            result = result.result,
            bet = totalBet,
            payout = result.payout,
            finalChips = playerChips
        )
        
        currentBet = 0
        numberOfHands = 1
        totalBet = 0
        
        // Solicitar nueva apuesta si tiene fichas
        if (playerChips >= gameSettings.minBet) {
            delay(1000)
            sendMessage(ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, playerChips),
                currentChips = playerChips
            ))
        } else {
            sendMessage(ServerMessage.Error("¡Te has quedado sin fichas! Inicia una nueva sesión para continuar."))
        }
    }

    /**
     * Maneja la solicitud de nueva partida
     */
    private suspend fun handleNewGame() {
        if (gameMode == null || !::dealerAI.isInitialized) {
            sendError("Debes unirte primero al juego")
            return
        }
        
        if (playerChips < gameSettings.minBet) {
            // Restaurar fichas iniciales para nueva sesión
            playerChips = gameSettings.initialChips
            println("🔄 $playerName reinicia con ${gameSettings.initialChips} fichas")
        }
        
        sendMessage(ServerMessage.RequestBet(
            minBet = gameSettings.minBet,
            maxBet = minOf(gameSettings.maxBet, playerChips),
            currentChips = playerChips
        ))
    }

    /**
     * Maneja la solicitud de records
     */
    private suspend fun handleRequestRecords() {
        val records = recordsManager.getTopRecords()
        sendMessage(ServerMessage.RecordsList(records))
    }

    /**
     * Maneja la solicitud de historial de manos
     */
    private suspend fun handleRequestHistory() {
        sendMessage(ServerMessage.HandHistoryList(handHistory.toList()))
    }

    /**
     * Maneja la selección de mano activa (para múltiples manos)
     */
    private suspend fun handleSelectHand(message: ClientMessage.SelectHand) {
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }
        
        val success = dealerAI.selectHand(playerId, message.handIndex)
        if (success) {
            val gameState = dealerAI.getCurrentState(playerId, playerChips - totalBet)
            sendMessage(gameState)
        } else {
            sendError("No se puede seleccionar esa mano")
        }
    }

    /**
     * Maneja el ping
     */
    private suspend fun handlePing() {
        sendMessage(ServerMessage.Pong)
    }

    /**
     * Envía un mensaje al cliente
     */
    private suspend fun sendMessage(message: ServerMessage) = withContext(Dispatchers.IO) {
        try {
            val jsonMessage = json.encodeToString(message)
            output.write(jsonMessage)
            output.newLine()
            output.flush()
        } catch (e: Exception) {
            println("❌ Error al enviar mensaje a $playerName: ${e.message}")
            throw e
        }
    }

    /**
     * Envía un mensaje de error
     */
    private suspend fun sendError(errorMessage: String) {
        sendMessage(ServerMessage.Error(errorMessage))
    }

    /**
     * Limpieza de recursos
     */
    private fun cleanup() {
        try {
            socket.close()
            println("🧹 Conexión cerrada limpiamente: $playerName")
        } catch (e: Exception) {
            println("⚠️ Error al cerrar conexión: ${e.message}")
        }
    }
}
