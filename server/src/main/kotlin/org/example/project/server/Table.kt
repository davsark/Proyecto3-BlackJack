package org.example.project.server

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.model.Deck
import org.example.project.model.Hand
import org.example.project.protocol.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Mesa de Blackjack compartida para modo PvP
 * 
 * Gestiona múltiples jugadores en la misma mesa:
 * - Un solo dealer compartido
 * - Un solo mazo compartido
 * - Turnos secuenciales
 * - Broadcast de estados a todos los jugadores
 */
class Table(
    val tableId: String,
    private val gameSettings: GameSettings,
    private val maxPlayers: Int = 4
) {
    // Estado de la mesa
    private val mutex = Mutex()
    private val deck = Deck(gameSettings.numberOfDecks)
    private val dealerHand = Hand()
    
    // Jugadores en la mesa
    private val players = ConcurrentHashMap<String, TablePlayer>()
    private val playerOrder = mutableListOf<String>() // Orden de turnos
    
    // Estado del juego
    private var tablePhase = TableGamePhase.WAITING_FOR_PLAYERS
    private var currentPlayerIndex = 0
    private var roundNumber = 0
    
    // Callbacks para notificar a los clientes
    private val clientCallbacks = ConcurrentHashMap<String, suspend (ServerMessage) -> Unit>()

    init {
        deck.shuffle()
        println("🎰 Mesa $tableId creada (máx $maxPlayers jugadores)")
    }

    /**
     * Datos de un jugador en la mesa
     */
    data class TablePlayer(
        val playerId: String,
        val name: String,
        var chips: Int,
        var currentBet: Int = 0,
        val hand: Hand = Hand(),
        var status: PlayerStatus = PlayerStatus.WAITING,
        var isReady: Boolean = false
    )

    enum class PlayerStatus {
        WAITING,        // Esperando que empiece la ronda
        BETTING,        // Eligiendo apuesta
        PLAYING,        // Su turno de jugar
        STANDING,       // Se plantó
        BUSTED,         // Se pasó
        BLACKJACK,      // Tiene blackjack
        FINISHED        // Terminó su turno
    }

    enum class TableGamePhase {
        WAITING_FOR_PLAYERS,  // Esperando que se unan jugadores
        BETTING,              // Fase de apuestas
        DEALING,              // Repartiendo cartas
        PLAYER_TURNS,         // Turnos de jugadores
        DEALER_TURN,          // Turno del dealer
        RESOLVING,            // Resolviendo resultados
        ROUND_END             // Fin de ronda
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GESTIÓN DE JUGADORES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Añade un jugador a la mesa
     */
    suspend fun addPlayer(
        playerId: String, 
        name: String, 
        chips: Int,
        callback: suspend (ServerMessage) -> Unit
    ): Boolean = mutex.withLock {
        if (players.size >= maxPlayers) {
            return false
        }
        
        if (tablePhase != TableGamePhase.WAITING_FOR_PLAYERS &&
            tablePhase != TableGamePhase.ROUND_END &&
            tablePhase != TableGamePhase.BETTING) {
            // No se puede unir en medio de una ronda activa
            return false
        }

        val player = TablePlayer(playerId, name, chips)
        players[playerId] = player
        playerOrder.add(playerId)
        clientCallbacks[playerId] = callback

        println("👤 $name se une a mesa $tableId (${players.size}/$maxPlayers)")

        if (tablePhase == TableGamePhase.BETTING) {
            // El jugador llega mientras otros están apostando — ponerlo a apostar también
            player.status = PlayerStatus.BETTING
            broadcastTableState()
            sendToPlayer(playerId, ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, player.chips),
                currentChips = player.chips
            ))
        } else {
            // Notificar a todos los jugadores
            broadcastTableState()

            // Si hay al menos 1 jugador, iniciar fase de apuestas
            if (players.size >= 1 && tablePhase == TableGamePhase.WAITING_FOR_PLAYERS) {
                startBettingPhase()
            }
        }

        return true
    }

    /**
     * Elimina un jugador de la mesa
     */
    suspend fun removePlayer(playerId: String) = mutex.withLock {
        players.remove(playerId)
        playerOrder.remove(playerId)
        clientCallbacks.remove(playerId)
        
        println("👋 Jugador $playerId abandona mesa $tableId (${players.size}/$maxPlayers)")
        
        // Si era su turno, pasar al siguiente
        if (tablePhase == TableGamePhase.PLAYER_TURNS) {
            val currentPlayerId = playerOrder.getOrNull(currentPlayerIndex)
            if (currentPlayerId == playerId) {
                advanceToNextPlayer()
            }
        }
        
        broadcastTableState()
        
        // Si no quedan jugadores, resetear mesa
        if (players.isEmpty()) {
            resetTable()
        }
    }

    /**
     * Verifica si la mesa está llena
     */
    fun isFull(): Boolean = players.size >= maxPlayers

    /**
     * Verifica si la mesa está vacía
     */
    fun isEmpty(): Boolean = players.isEmpty()

    /**
     * Obtiene el número de jugadores
     */
    fun getPlayerCount(): Int = players.size

    // ═══════════════════════════════════════════════════════════════════════
    // FASE DE APUESTAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicia la fase de apuestas
     */
    private suspend fun startBettingPhase() {
        tablePhase = TableGamePhase.BETTING
        roundNumber++
        
        println("💰 Mesa $tableId - Ronda $roundNumber - Fase de apuestas")
        
        // Resetear estado de jugadores
        players.values.forEach { player ->
            player.status = PlayerStatus.BETTING
            player.currentBet = 0
            player.hand.clear()
            player.isReady = false
        }
        
        // Limpiar dealer
        dealerHand.clear()
        
        // Notificar a todos
        broadcastTableState()
        
        // Enviar solicitud de apuesta a cada jugador
        players.values.forEach { player ->
            sendToPlayer(player.playerId, ServerMessage.RequestBet(
                minBet = gameSettings.minBet,
                maxBet = minOf(gameSettings.maxBet, player.chips),
                currentChips = player.chips
            ))
        }
    }

    /**
     * Procesa la apuesta de un jugador
     */
    suspend fun placeBet(playerId: String, amount: Int): Boolean = mutex.withLock {
        val player = players[playerId] ?: return false
        
        if (tablePhase != TableGamePhase.BETTING) {
            sendToPlayer(playerId, ServerMessage.Error("No es momento de apostar"))
            return false
        }
        
        if (amount < gameSettings.minBet || amount > player.chips) {
            sendToPlayer(playerId, ServerMessage.Error("Apuesta inválida"))
            return false
        }
        
        player.currentBet = amount
        player.chips -= amount
        player.isReady = true
        player.status = PlayerStatus.WAITING
        
        println("💵 ${player.name} apuesta $amount en mesa $tableId")
        
        broadcastTableState()
        
        // Verificar si todos apostaron
        if (players.values.all { it.isReady }) {
            startDealingPhase()
        }
        
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FASE DE REPARTO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reparte las cartas iniciales
     */
    private suspend fun startDealingPhase() {
        tablePhase = TableGamePhase.DEALING
        
        println("🎴 Mesa $tableId - Repartiendo cartas")
        
        // Verificar/reiniciar mazo
        if (deck.needsReset()) {
            deck.reset()
            deck.shuffle()
        }
        
        // Primera carta a cada jugador
        playerOrder.forEach { playerId ->
            players[playerId]?.hand?.addCard(deck.dealCard(hidden = false))
        }
        
        // Primera carta al dealer (visible)
        dealerHand.addCard(deck.dealCard(hidden = false))
        
        // Segunda carta a cada jugador
        playerOrder.forEach { playerId ->
            players[playerId]?.hand?.addCard(deck.dealCard(hidden = false))
        }
        
        // Segunda carta al dealer (oculta)
        dealerHand.addCard(deck.dealCard(hidden = true))
        
        // Verificar blackjacks
        players.values.forEach { player ->
            if (player.hand.isBlackjack()) {
                player.status = PlayerStatus.BLACKJACK
                println("🎰 ${player.name} tiene BLACKJACK!")
            }
        }
        
        broadcastTableState()
        
        // Pequeña pausa para efecto visual
        delay(500)
        
        // Iniciar turnos de jugadores
        startPlayerTurns()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TURNOS DE JUGADORES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicia la fase de turnos de jugadores
     */
    private suspend fun startPlayerTurns() {
        tablePhase = TableGamePhase.PLAYER_TURNS
        currentPlayerIndex = 0
        
        // Encontrar primer jugador que pueda jugar
        findNextActivePlayer()
        
        if (currentPlayerIndex >= playerOrder.size) {
            // Todos tienen blackjack o están finished
            startDealerTurn()
        } else {
            val currentPlayerId = playerOrder[currentPlayerIndex]
            players[currentPlayerId]?.status = PlayerStatus.PLAYING
            
            println("🎮 Turno de ${players[currentPlayerId]?.name}")
            
            broadcastTableState()
        }
    }

    /**
     * Encuentra el siguiente jugador que puede jugar
     */
    private fun findNextActivePlayer() {
        while (currentPlayerIndex < playerOrder.size) {
            val playerId = playerOrder[currentPlayerIndex]
            val player = players[playerId]
            
            // Saltar jugadores que ya terminaron
            if (player?.status == PlayerStatus.BLACKJACK ||
                player?.status == PlayerStatus.BUSTED ||
                player?.status == PlayerStatus.STANDING ||
                player?.status == PlayerStatus.FINISHED) {
                currentPlayerIndex++
            } else {
                break
            }
        }
    }

    /**
     * Avanza al siguiente jugador
     */
    private suspend fun advanceToNextPlayer() {
        // Marcar jugador actual como terminado si aún está jugando
        val currentPlayerId = playerOrder.getOrNull(currentPlayerIndex)
        currentPlayerId?.let { id ->
            val player = players[id]
            if (player?.status == PlayerStatus.PLAYING) {
                player.status = PlayerStatus.FINISHED
            }
        }
        
        currentPlayerIndex++
        findNextActivePlayer()
        
        if (currentPlayerIndex >= playerOrder.size) {
            // Todos los jugadores terminaron
            startDealerTurn()
        } else {
            val nextPlayerId = playerOrder[currentPlayerIndex]
            players[nextPlayerId]?.status = PlayerStatus.PLAYING
            
            println("🎮 Turno de ${players[nextPlayerId]?.name}")
            
            broadcastTableState()
        }
    }

    /**
     * Verifica si es el turno del jugador
     */
    private fun isPlayerTurn(playerId: String): Boolean {
        if (tablePhase != TableGamePhase.PLAYER_TURNS) return false
        val currentPlayerId = playerOrder.getOrNull(currentPlayerIndex) ?: return false
        return currentPlayerId == playerId
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACCIONES DE JUGADOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Jugador pide carta (HIT)
     */
    suspend fun playerHit(playerId: String): Boolean = mutex.withLock {
        if (!isPlayerTurn(playerId)) {
            sendToPlayer(playerId, ServerMessage.Error("No es tu turno"))
            return false
        }
        
        val player = players[playerId] ?: return false
        
        val card = deck.dealCard(hidden = false)
        player.hand.addCard(card)
        
        println("🃏 ${player.name} pide carta: $card (Total: ${player.hand.calculateValue()})")
        
        if (player.hand.isBusted()) {
            player.status = PlayerStatus.BUSTED
            println("💥 ${player.name} se pasó!")
            broadcastTableState()
            delay(300)
            advanceToNextPlayer()
        } else if (player.hand.calculateValue() == 21) {
            player.status = PlayerStatus.STANDING
            println("🎯 ${player.name} alcanza 21!")
            broadcastTableState()
            delay(300)
            advanceToNextPlayer()
        } else {
            broadcastTableState()
        }
        
        return true
    }

    /**
     * Jugador se planta (STAND)
     */
    suspend fun playerStand(playerId: String): Boolean = mutex.withLock {
        if (!isPlayerTurn(playerId)) {
            sendToPlayer(playerId, ServerMessage.Error("No es tu turno"))
            return false
        }
        
        val player = players[playerId] ?: return false
        
        player.status = PlayerStatus.STANDING
        println("✋ ${player.name} se planta con ${player.hand.calculateValue()}")
        
        broadcastTableState()
        delay(300)
        advanceToNextPlayer()
        
        return true
    }

    /**
     * Jugador dobla (DOUBLE)
     */
    suspend fun playerDouble(playerId: String): Boolean = mutex.withLock {
        if (!isPlayerTurn(playerId)) {
            sendToPlayer(playerId, ServerMessage.Error("No es tu turno"))
            return false
        }
        
        val player = players[playerId] ?: return false
        
        if (player.hand.getCards().size != 2) {
            sendToPlayer(playerId, ServerMessage.Error("Solo puedes doblar con 2 cartas"))
            return false
        }
        
        if (player.currentBet > player.chips) {
            sendToPlayer(playerId, ServerMessage.Error("No tienes suficientes fichas"))
            return false
        }
        
        // Doblar apuesta
        player.chips -= player.currentBet
        player.currentBet *= 2
        
        // Dar una carta
        val card = deck.dealCard(hidden = false)
        player.hand.addCard(card)
        
        println("🎲 ${player.name} dobla: $card (Total: ${player.hand.calculateValue()})")
        
        player.status = if (player.hand.isBusted()) PlayerStatus.BUSTED else PlayerStatus.STANDING
        
        broadcastTableState()
        delay(300)
        advanceToNextPlayer()
        
        return true
    }

    /**
     * Jugador se rinde (SURRENDER)
     */
    suspend fun playerSurrender(playerId: String): Boolean = mutex.withLock {
        if (!isPlayerTurn(playerId)) {
            sendToPlayer(playerId, ServerMessage.Error("No es tu turno"))
            return false
        }
        
        val player = players[playerId] ?: return false
        
        if (player.hand.getCards().size != 2) {
            sendToPlayer(playerId, ServerMessage.Error("Solo puedes rendirte con 2 cartas"))
            return false
        }
        
        // Devolver mitad de la apuesta
        val refund = player.currentBet / 2
        player.chips += refund
        player.currentBet = player.currentBet / 2  // Pierde la otra mitad
        
        player.status = PlayerStatus.FINISHED
        
        println("🏳️ ${player.name} se rinde")
        
        broadcastTableState()
        delay(300)
        advanceToNextPlayer()
        
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TURNO DEL DEALER
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Turno del dealer
     */
    private suspend fun startDealerTurn() {
        tablePhase = TableGamePhase.DEALER_TURN
        
        // Revelar carta oculta
        dealerHand.revealAll()
        
        println("🎰 Turno del dealer - Revela: ${dealerHand.calculateValue()}")
        
        broadcastTableState()
        delay(500)
        
        // Verificar si hay jugadores activos (no todos busted)
        val hasActivePlayers = players.values.any { 
            it.status == PlayerStatus.STANDING || it.status == PlayerStatus.BLACKJACK 
        }
        
        if (hasActivePlayers) {
            // Dealer juega
            while (shouldDealerHit()) {
                val card = deck.dealCard(hidden = false)
                dealerHand.addCard(card)
                
                println("🎰 Dealer pide: $card (Total: ${dealerHand.calculateValue()})")
                
                broadcastTableState()
                delay(500)
            }
            
            if (dealerHand.isBusted()) {
                println("💥 Dealer se pasó con ${dealerHand.calculateValue()}")
            } else {
                println("✋ Dealer se planta con ${dealerHand.calculateValue()}")
            }
        } else {
            println("🎰 Dealer no juega (todos los jugadores se pasaron)")
        }
        
        broadcastTableState()
        delay(500)
        
        // Resolver resultados
        resolveRound()
    }

    /**
     * Determina si el dealer debe pedir
     */
    private fun shouldDealerHit(): Boolean {
        val value = dealerHand.calculateValue()
        return if (gameSettings.dealerHitsOnSoft17) {
            value < 17 || (value == 17 && dealerHand.isSoft())
        } else {
            value < 17
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOLUCIÓN DE RONDA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resuelve la ronda y calcula pagos
     */
    private suspend fun resolveRound() {
        tablePhase = TableGamePhase.RESOLVING
        
        val dealerValue = dealerHand.calculateValue()
        val dealerBusted = dealerHand.isBusted()
        val dealerBlackjack = dealerHand.isBlackjack()
        
        println("📊 Resolviendo ronda - Dealer: $dealerValue")
        
        players.values.forEach { player ->
            val playerValue = player.hand.calculateValue()
            
            val (result, payout) = when {
                player.status == PlayerStatus.BUSTED -> {
                    GameResultType.LOSE to 0
                }
                player.status == PlayerStatus.BLACKJACK -> {
                    if (dealerBlackjack) {
                        GameResultType.PUSH to player.currentBet
                    } else {
                        val bjPayout = (player.currentBet * gameSettings.blackjackPayout).toInt()
                        GameResultType.BLACKJACK to (player.currentBet + bjPayout)
                    }
                }
                dealerBusted -> {
                    GameResultType.WIN to (player.currentBet * 2)
                }
                dealerBlackjack -> {
                    GameResultType.LOSE to 0
                }
                playerValue > dealerValue -> {
                    GameResultType.WIN to (player.currentBet * 2)
                }
                playerValue < dealerValue -> {
                    GameResultType.LOSE to 0
                }
                else -> {
                    GameResultType.PUSH to player.currentBet
                }
            }
            
            player.chips += payout
            
            val netResult = payout - player.currentBet
            println("   ${player.name}: $playerValue vs $dealerValue → $result (${if (netResult >= 0) "+$netResult" else netResult})")
            
            // Enviar resultado individual
            sendToPlayer(player.playerId, ServerMessage.GameResult(
                result = result,
                playerFinalScore = playerValue,
                dealerFinalScore = dealerValue,
                dealerFinalHand = dealerHand.getCards(),
                payout = netResult,
                newChipsTotal = player.chips,
                message = buildResultMessage(result, player.name, playerValue, dealerValue),
                handResults = emptyList()
            ))
        }
        
        broadcastTableState()
        
        // Esperar antes de nueva ronda
        delay(3000)
        
        // Eliminar jugadores sin fichas
        val playersToRemove = players.values.filter { it.chips < gameSettings.minBet }.map { it.playerId }
        playersToRemove.forEach { playerId ->
            sendToPlayer(playerId, ServerMessage.Error("Te has quedado sin fichas suficientes"))
            // No eliminamos aquí, el cliente debe desconectarse
        }
        
        // Nueva ronda si quedan jugadores con fichas
        if (players.values.any { it.chips >= gameSettings.minBet }) {
            tablePhase = TableGamePhase.ROUND_END
            broadcastTableState()
            delay(1000)
            startBettingPhase()
        } else {
            tablePhase = TableGamePhase.WAITING_FOR_PLAYERS
            broadcastTableState()
        }
    }

    /**
     * Construye mensaje de resultado
     */
    private fun buildResultMessage(result: GameResultType, playerName: String, playerValue: Int, dealerValue: Int): String {
        return when (result) {
            GameResultType.BLACKJACK -> "¡$playerName tiene BLACKJACK!"
            GameResultType.WIN -> "¡$playerName gana! $playerValue vs $dealerValue"
            GameResultType.LOSE -> "$playerName pierde. $playerValue vs $dealerValue"
            GameResultType.PUSH -> "Empate para $playerName. Ambos tienen $playerValue"
            GameResultType.SURRENDER -> "$playerName se rindió"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMUNICACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Envía mensaje a un jugador específico
     */
    private suspend fun sendToPlayer(playerId: String, message: ServerMessage) {
        clientCallbacks[playerId]?.invoke(message)
    }

    /**
     * Envía el estado de la mesa a todos los jugadores
     */
    private suspend fun broadcastTableState() {
        players.keys.forEach { odPlayerId ->
            val state = buildTableStateForPlayer(odPlayerId)
            sendToPlayer(odPlayerId, state)
        }
    }

    /**
     * Construye el estado de la mesa para un jugador específico
     */
    private fun buildTableStateForPlayer(forPlayerId: String): ServerMessage.PvPTableState {
        val currentTurnPlayerId = playerOrder.getOrNull(currentPlayerIndex)
        
        val playerInfos = playerOrder.mapNotNull { odPlayerId ->
            players[odPlayerId]?.let { player ->
                PvPPlayerInfo(
                    playerId = player.playerId,
                    name = player.name,
                    chips = player.chips,
                    currentBet = player.currentBet,
                    cards = player.hand.getCards(),
                    score = player.hand.calculateValue(),
                    status = player.status.name,
                    isCurrentTurn = odPlayerId == currentTurnPlayerId,
                    isBusted = player.status == PlayerStatus.BUSTED,
                    isBlackjack = player.status == PlayerStatus.BLACKJACK,
                    isStanding = player.status == PlayerStatus.STANDING
                )
            }
        }
        
        val dealerCards = dealerHand.getCards()
        val dealerScore = if (tablePhase == TableGamePhase.DEALER_TURN || 
                              tablePhase == TableGamePhase.RESOLVING ||
                              tablePhase == TableGamePhase.ROUND_END) {
            dealerHand.calculateValue()
        } else {
            // Solo mostrar valor de carta visible
            dealerHand.getVisibleCards().sumOf { it.rank.value }
        }
        
        return ServerMessage.PvPTableState(
            tableId = tableId,
            phase = tablePhase.name,
            roundNumber = roundNumber,
            players = playerInfos,
            dealerCards = dealerCards,
            dealerScore = dealerScore,
            currentTurnPlayerId = currentTurnPlayerId,
            currentPlayerId = forPlayerId,
            minBet = gameSettings.minBet,
            maxBet = gameSettings.maxBet
        )
    }

    /**
     * Resetea la mesa
     */
    private fun resetTable() {
        tablePhase = TableGamePhase.WAITING_FOR_PLAYERS
        currentPlayerIndex = 0
        roundNumber = 0
        dealerHand.clear()
        deck.reset()
        deck.shuffle()
        
        println("🔄 Mesa $tableId reseteada")
    }
}
