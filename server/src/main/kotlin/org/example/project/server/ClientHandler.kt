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
 * Maneja la comunicacion con un cliente individual
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

    private val deck = Deck(gameSettings.numberOfDecks)
    private lateinit var dealerAI: DealerAI
    private var gameMode: GameMode? = null

    private var currentTable: Table? = null

    private var playerChips: Int = gameSettings.initialChips
    private var currentBet: Int = 0
    private var isInGame: Boolean = false

    private var numberOfHands: Int = 1
    private var totalBet: Int = 0

    private val handHistory = mutableListOf<HandHistory>()

    init {
        deck.shuffle()
    }

    suspend fun handle() = coroutineScope {
        try {
            input = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            socket.soTimeout = GameConfig.CONNECTION_TIMEOUT_MS.toInt()

            println("✅ Cliente conectado: ${socket.inetAddress.hostAddress}:${socket.port}")

            while (socket.isConnected && !socket.isClosed) {
                val line = try {
                    input.readLine()
                } catch (e: SocketTimeoutException) {
                    println("⏱️ Timeout del cliente $playerName")
                    break
                } catch (e: SocketException) {
                    println("🔌 Conexion cerrada: $playerName")
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
            if (gameMode == GameMode.PVP) {
                tableManager.removePlayer(playerId)
            }
            cleanup()
        }
    }

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

    private suspend fun handleJoinGame(message: ClientMessage.JoinGame) {
        playerName = message.playerName
        gameMode = message.gameMode
        playerChips = message.buyIn.coerceIn(0, gameSettings.initialChips)

        println("👤 $playerName se une (Modo: ${message.gameMode}, Fichas: $playerChips)")

        when (message.gameMode) {
            GameMode.PVE -> {
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
                sendMessage(ServerMessage.JoinConfirmation(
                    playerId = playerId,
                    message = "Bienvenido $playerName. Buscando mesa PvP...",
                    initialChips = playerChips
                ))

                val table = tableManager.findOrCreateTable(
                    playerId = playerId,
                    playerName = playerName,
                    chips = playerChips,
                    callback = { msg -> sendMessage(msg) }
                )

                if (table != null) {
                    currentTable = table
                    println("🎰 $playerName unido a mesa PvP: ${table.tableId}")
                } else {
                    sendError("No se pudo unir a una mesa. Intenta de nuevo.")
                }
            }
        }
    }

    private suspend fun handlePlaceBet(message: ClientMessage.PlaceBet) {
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.placeBet(playerId, message.amount)
            return
        }

        val betAmount = message.amount
        val numHands = message.numberOfHands.coerceIn(1, 4)
        val totalBetRequired = betAmount * numHands

        if (betAmount < gameSettings.minBet) {
            sendError("La apuesta minima es ${gameSettings.minBet}")
            return
        }
        if (totalBetRequired > playerChips) {
            sendError("No tienes suficientes fichas. Necesitas: $totalBetRequired, Tienes: $playerChips")
            return
        }
        if (betAmount > gameSettings.maxBet) {
            sendError("La apuesta maxima es ${gameSettings.maxBet}")
            return
        }

        currentBet = betAmount
        numberOfHands = numHands
        totalBet = totalBetRequired
        isInGame = true

        println("💰 $playerName apuesta $currentBet fichas x $numberOfHands manos = $totalBet total")
        startGame()
    }

    private suspend fun startGame() {
        dealerAI.checkAndResetDeck()
        val gameState = dealerAI.startNewGame(playerId, currentBet, playerChips - totalBet, numberOfHands)
        sendMessage(gameState)

        if (numberOfHands == 1 && gameState.playerScore == 21 && gameState.playerHand.size == 2) {
            delay(500)
            finishGame()
        }
    }

    private suspend fun handleRequestCard() {
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerHit(playerId)
            return
        }

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

    private suspend fun handleStand() {
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerStand(playerId)
            return
        }

        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        val gameState = dealerAI.playerStand(playerId)
        sendMessage(gameState)

        if (gameState.gameState == GamePhase.GAME_OVER) {
            delay(500)
            finishGame()
        }
    }

    private suspend fun handleDouble() {
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerDouble(playerId)
            return
        }

        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

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

        if (result.gameState == GamePhase.GAME_OVER) {
            delay(500)
            finishGame()
        }
    }

    private suspend fun handleSplit() {
        if (gameMode == GameMode.PVP) {
            sendError("Split no disponible en modo PvP")
            return
        }

        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

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

    private suspend fun handleSurrender() {
        if (gameMode == GameMode.PVP && currentTable != null) {
            currentTable?.playerSurrender(playerId)
            return
        }

        if (!isInGame || !::dealerAI.isInitialized) {
            sendError("No hay juego activo")
            return
        }

        if (!gameSettings.allowSurrender) {
            sendError("La rendicion no esta permitida en esta mesa")
            return
        }

        val gameStateResult = dealerAI.playerSurrender(playerId)
        if (gameStateResult == null) {
            sendError("No puedes rendirte en este momento")
            return
        }

        sendMessage(gameStateResult)

        val surrenderResult = dealerAI.getSurrenderResult(playerId, currentBet, playerChips)
        playerChips = surrenderResult.newChipsTotal
        isInGame = false

        println("🏳️ $playerName se rinde. Fichas: $playerChips")
        sendMessage(surrenderResult)

        val historyEntry = HandHistory(
            playerHand = dealerAI.getPlayerHand(playerId),
            dealerHand = surrenderResult.dealerFinalHand,
            result = surrenderResult.result,
            bet = totalBet,
            payout = surrenderResult.payout,
            timestamp = System.currentTimeMillis(),
            playerScore = surrenderResult.playerFinalScore,
            dealerScore = surrenderResult.dealerFinalScore
        )
        handHistory.add(0, historyEntry)
        if (handHistory.size > 10) handHistory.removeAt(handHistory.size - 1)

        recordsManager.recordGameResult(
            playerName = playerName,
            result = surrenderResult.result,
            bet = totalBet,
            payout = surrenderResult.payout,
            finalChips = playerChips
        )

        currentBet = 0
        numberOfHands = 1
        totalBet = 0

        if (playerChips >= gameSettings.minBet) {
            delay(1000)
            sendMessage(ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, playerChips),
                currentChips = playerChips
            ))
        } else {
            sendMessage(ServerMessage.Error("¡Te has quedado sin fichas! Inicia una nueva sesion para continuar."))
        }
    }

    private suspend fun finishGame() {
        val result = dealerAI.getGameResult(playerId, currentBet, playerChips)

        playerChips = result.newChipsTotal
        isInGame = false

        println("🏆 Resultado para $playerName: ${result.result} | Pago: ${result.payout} | Fichas: $playerChips")

        sendMessage(result)

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

        if (playerChips >= gameSettings.minBet) {
            delay(1000)
            sendMessage(ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, playerChips),
                currentChips = playerChips
            ))
        } else {
            sendMessage(ServerMessage.Error("¡Te has quedado sin fichas! Inicia una nueva sesion para continuar."))
        }
    }

    private suspend fun handleNewGame() {
        if (gameMode == null || !::dealerAI.isInitialized) {
            sendError("Debes unirte primero al juego")
            return
        }

        if (playerChips < gameSettings.minBet) {
            playerChips = gameSettings.initialChips
            println("🔄 $playerName reinicia con ${gameSettings.initialChips} fichas")
        }

        sendMessage(ServerMessage.RequestBet(
            minBet = gameSettings.minBet,
            maxBet = minOf(gameSettings.maxBet, playerChips),
            currentChips = playerChips
        ))
    }

    private suspend fun handleRequestRecords() {
        val records = recordsManager.getTopRecords()
        sendMessage(ServerMessage.RecordsList(records))
    }

    private suspend fun handleRequestHistory() {
        sendMessage(ServerMessage.HandHistoryList(handHistory.toList()))
    }

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

    private suspend fun handlePing() {
        sendMessage(ServerMessage.Pong)
    }

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

    private suspend fun sendError(errorMessage: String) {
        sendMessage(ServerMessage.Error(errorMessage))
    }

    private fun cleanup() {
        try {
            socket.close()
            println("🧹 Conexion cerrada limpiamente: $playerName")
        } catch (e: Exception) {
            println("⚠️ Error al cerrar conexion: ${e.message}")
        }
    }
}