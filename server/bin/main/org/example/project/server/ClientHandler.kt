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
 */
class ClientHandler(
    private val socket: Socket,
    private val recordsManager: RecordsManager,
    private val gameSettings: GameSettings
) {
    private val playerId = UUID.randomUUID().toString()
    private var playerName: String = "Jugador"
    private lateinit var input: BufferedReader
    private lateinit var output: BufferedWriter
    private val json = Json { ignoreUnknownKeys = true }

    // Estado del juego
    private val deck = Deck(gameSettings.numberOfDecks)
    private lateinit var dealerAI: DealerAI
    private var gameMode: GameMode? = null
    
    // Sistema de fichas y apuestas
    private var playerChips: Int = gameSettings.initialChips
    private var currentBet: Int = 0
    private var isInGame: Boolean = false

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
                dealerAI = DealerAI(deck, gameSettings)
                sendMessage(ServerMessage.JoinConfirmation(
                    playerId = playerId,
                    message = "Bienvenido $playerName. Modo: Jugador vs Dealer",
                    initialChips = playerChips
                ))
                // Enviar estado de la mesa
                sendMessage(ServerMessage.TableState(
                    players = listOf(PlayerInfo(playerName, 0, 0, false, playerChips, 0)),
                    minBet = gameSettings.minBet,
                    maxBet = gameSettings.maxBet,
                    currentPlayerChips = playerChips
                ))
                // Solicitar apuesta
                sendMessage(ServerMessage.RequestBet(
                    minBet = gameSettings.minBet,
                    maxBet = minOf(gameSettings.maxBet, playerChips),
                    currentChips = playerChips
                ))
            }
            GameMode.PVP -> {
                dealerAI = DealerAI(deck, gameSettings)
                sendMessage(ServerMessage.JoinConfirmation(
                    playerId = playerId,
                    message = "Bienvenido $playerName. Modo PVP - Compitiendo contra otros jugadores",
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
        }
        
        // Enviar records al conectar
        handleRequestRecords()
    }

    /**
     * Maneja la apuesta del jugador
     */
    private suspend fun handlePlaceBet(message: ClientMessage.PlaceBet) {
        val betAmount = message.amount
        
        // Validar apuesta
        if (betAmount < gameSettings.minBet) {
            sendError("La apuesta mínima es ${gameSettings.minBet}")
            return
        }
        if (betAmount > playerChips) {
            sendError("No tienes suficientes fichas. Tienes: $playerChips")
            return
        }
        if (betAmount > gameSettings.maxBet) {
            sendError("La apuesta máxima es ${gameSettings.maxBet}")
            return
        }
        
        currentBet = betAmount
        isInGame = true
        println("💰 $playerName apuesta $currentBet fichas")
        
        // Iniciar la partida
        startGame()
    }

    /**
     * Inicia una nueva partida después de la apuesta
     */
    private suspend fun startGame() {
        dealerAI.checkAndResetDeck()
        val gameState = dealerAI.startNewGame(playerId, currentBet, playerChips)
        sendMessage(gameState)

        // Verificar si hay Blackjack natural
        if (gameState.playerScore == 21 && gameState.playerHand.size == 2) {
            delay(500)
            finishGame()
        }
    }

    /**
     * Maneja la petición de carta
     */
    private suspend fun handleRequestCard() {
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
        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        val gameState = dealerAI.playerStand(playerId)
        sendMessage(gameState)

        delay(500)
        finishGame()
    }

    /**
     * Maneja cuando el jugador dobla
     */
    private suspend fun handleDouble() {
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
        
        currentBet *= 2
        println("🎲 $playerName dobla. Nueva apuesta: $currentBet")
        
        sendMessage(result)
        delay(500)
        finishGame()
    }

    /**
     * Maneja cuando el jugador divide
     */
    private suspend fun handleSplit() {
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
        val result = dealerAI.getGameResult(playerId, currentBet, playerChips)
        
        // Actualizar fichas
        playerChips = result.newChipsTotal
        isInGame = false
        
        println("🏆 Resultado para $playerName: ${result.result} | Pago: ${result.payout} | Fichas: $playerChips")
        
        sendMessage(result)

        // Guardar en records
        recordsManager.recordGameResult(
            playerName = playerName,
            result = result.result,
            bet = currentBet,
            payout = result.payout,
            finalChips = playerChips
        )
        
        currentBet = 0
        
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
