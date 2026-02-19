package org.example.project.server

import org.example.project.config.GameConfig
import org.example.project.model.Deck
import org.example.project.model.Hand
import org.example.project.protocol.*

/**
 * Inteligencia Artificial del Dealer para modo PVE y PVP
 * 
 * MÚLTIPLES MANOS:
 * - El jugador puede jugar 1, 2 o 3 manos simultáneas
 * - Cada mano se juega por separado, de izquierda a derecha
 * - Cuando todas las manos están completas, el dealer juega
 * - Cada mano se resuelve independientemente contra el dealer
 */
class DealerAI(
    private val deck: Deck,
    private val gameSettings: GameSettings
) {
    // Mano del dealer (compartida para todas las manos del jugador)
    private val dealerHand = Hand()
    
    // Sistema de múltiples manos
    private data class PlayerGameState(
        val hands: MutableList<Hand> = mutableListOf(),
        val bets: MutableList<Int> = mutableListOf(),
        val statuses: MutableList<HandStatus> = mutableListOf(),
        val doubled: MutableList<Boolean> = mutableListOf(),
        var activeHandIndex: Int = 0,
        var numberOfHands: Int = 1
    )
    
    private val playerStates = mutableMapOf<String, PlayerGameState>()

    /**
     * Inicia una nueva partida
     * @param numberOfHands Número de manos a jugar (1-3)
     */
    fun startNewGame(playerId: String, bet: Int, playerChips: Int, numberOfHands: Int = 1): ServerMessage.GameState {
        // Limpiar estado anterior
        dealerHand.clear()
        
        // Crear nuevo estado del jugador
        val state = PlayerGameState(
            numberOfHands = numberOfHands.coerceIn(1, 4)
        )
        
        // Inicializar cada mano
        for (i in 0 until state.numberOfHands) {
            state.hands.add(Hand())
            state.bets.add(bet)
            state.statuses.add(if (i == 0) HandStatus.PLAYING else HandStatus.WAITING)
            state.doubled.add(false)
        }
        
        playerStates[playerId] = state
        
        // Repartir cartas (como en casino real: una carta a cada mano, luego dealer, luego segunda ronda)
        // Primera carta a cada mano
        for (hand in state.hands) {
            hand.addCard(deck.dealCard(hidden = false))
        }
        
        // Primera carta al dealer (visible)
        dealerHand.addCard(deck.dealCard(hidden = false))
        
        // Segunda carta a cada mano
        for (hand in state.hands) {
            hand.addCard(deck.dealCard(hidden = false))
        }
        
        // Segunda carta al dealer (oculta)
        dealerHand.addCard(deck.dealCard(hidden = true))
        
        // Log
        println("🎴 Nueva partida iniciada (${state.numberOfHands} mano(s), Apuesta: $bet cada una)")
        for ((index, hand) in state.hands.withIndex()) {
            println("   Mano ${index + 1}: ${hand.getCards()} = ${hand.calculateValue()}")
        }
        println("   Dealer: ${dealerHand.getVisibleCards()} + [oculta]")
        
        // Verificar blackjack natural en primera mano
        val firstHand = state.hands[0]
        if (firstHand.isBlackjack()) {
            state.statuses[0] = HandStatus.BLACKJACK
            // Si solo hay una mano, pasar al dealer inmediatamente
            if (state.numberOfHands == 1) {
                return playDealerTurn(playerId, playerChips)
            } else {
                // Avanzar a la siguiente mano
                advanceToNextHand(playerId)
            }
        }
        
        return buildGameState(playerId, GamePhase.PLAYER_TURN, playerChips)
    }

    /**
     * Jugador pide carta (HIT)
     */
    fun playerHit(playerId: String): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val hand = state.hands[state.activeHandIndex]
        val chips = estimatePlayerChips(playerId)
        
        // Repartir carta
        val newCard = deck.dealCard(hidden = false)
        hand.addCard(newCard)
        
        println("🃏 Mano ${state.activeHandIndex + 1} pide carta: $newCard (Total: ${hand.calculateValue()})")
        
        // Verificar resultado
        return when {
            hand.isBusted() -> {
                println("💥 Mano ${state.activeHandIndex + 1} se pasó! (${hand.calculateValue()})")
                state.statuses[state.activeHandIndex] = HandStatus.BUSTED
                handleHandComplete(playerId, chips)
            }
            hand.calculateValue() == 21 -> {
                println("🎯 Mano ${state.activeHandIndex + 1} alcanza 21")
                state.statuses[state.activeHandIndex] = HandStatus.STANDING
                handleHandComplete(playerId, chips)
            }
            else -> {
                buildGameState(playerId, GamePhase.PLAYER_TURN, chips)
            }
        }
    }

    /**
     * Jugador se planta (STAND)
     */
    fun playerStand(playerId: String): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val hand = state.hands[state.activeHandIndex]
        
        println("✋ Mano ${state.activeHandIndex + 1} se planta con ${hand.calculateValue()}")
        state.statuses[state.activeHandIndex] = HandStatus.STANDING
        
        return handleHandComplete(playerId, estimatePlayerChips(playerId))
    }

    /**
     * Jugador dobla (DOUBLE)
     */
    fun playerDouble(playerId: String): ServerMessage.GameState? {
        val state = playerStates[playerId] ?: return null
        val hand = state.hands[state.activeHandIndex]
        
        // Solo se puede doblar con 2 cartas
        if (hand.getCards().size != 2) return null
        
        // Doblar la apuesta
        state.bets[state.activeHandIndex] = state.bets[state.activeHandIndex] * 2
        state.doubled[state.activeHandIndex] = true
        
        // Dar una carta
        val newCard = deck.dealCard(hidden = false)
        hand.addCard(newCard)
        
        println("🎲 Mano ${state.activeHandIndex + 1} dobla: $newCard (Total: ${hand.calculateValue()})")
        
        // Marcar como completada
        state.statuses[state.activeHandIndex] = if (hand.isBusted()) HandStatus.BUSTED else HandStatus.STANDING
        
        return handleHandComplete(playerId, estimatePlayerChips(playerId))
    }

    /**
     * Jugador divide (SPLIT) - Permite re-splits hasta gameSettings.maxSplits
     */
    fun playerSplit(playerId: String): ServerMessage.GameState? {
        val state = playerStates[playerId] ?: return null

        // Permitir splits hasta gameSettings.maxSplits veces
        if (state.numberOfHands >= gameSettings.maxSplits + 1) return null

        val activeIdx = state.activeHandIndex
        val hand = state.hands[activeIdx]
        val cards = hand.getCards()

        if (cards.size != 2) return null
        if (cards[0].rank.value != cards[1].rank.value) return null

        val bet = state.bets[activeIdx]

        // Crear nueva mano con la segunda carta de la mano activa
        val newHand = Hand()
        newHand.addCard(cards[1])

        // Quitar segunda carta de la mano activa y dar una nueva
        hand.removeLastCard()
        hand.addCard(deck.dealCard(hidden = false))
        newHand.addCard(deck.dealCard(hidden = false))

        // Insertar la nueva mano justo después de la activa
        val insertIdx = activeIdx + 1
        state.hands.add(insertIdx, newHand)
        state.bets.add(insertIdx, bet)
        state.statuses.add(insertIdx, HandStatus.WAITING)
        state.doubled.add(insertIdx, false)
        state.numberOfHands++

        println("✂️ Split mano ${activeIdx + 1}: ${hand.calculateValue()} / ${newHand.calculateValue()} (total ${state.numberOfHands} manos)")

        if (hand.calculateValue() == 21) {
            state.statuses[activeIdx] = HandStatus.STANDING
            return handleHandComplete(playerId, estimatePlayerChips(playerId))
        }

        return buildGameState(playerId, GamePhase.PLAYER_TURN, estimatePlayerChips(playerId))
    }

    /**
     * Jugador se rinde (SURRENDER)
     */
    fun playerSurrender(playerId: String): ServerMessage.GameState? {
        val state = playerStates[playerId] ?: return null
        
        // Solo se puede rendir en la primera mano con 2 cartas
        if (state.activeHandIndex != 0) return null
        if (state.hands[0].getCards().size != 2) return null
        
        println("🏳️ Jugador se rinde")
        
        // Marcar como rendido - se resuelve después
        state.statuses[0] = HandStatus.COMPLETED
        
        // Revelar cartas del dealer
        dealerHand.revealAll()
        
        return buildSurrenderResult(playerId, estimatePlayerChips(playerId))
    }

    /**
     * Maneja cuando una mano se completa (bust, stand, 21, double)
     */
    private fun handleHandComplete(playerId: String, chips: Int): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        
        // Intentar avanzar a la siguiente mano
        if (advanceToNextHand(playerId)) {
            println("➡️ Avanzando a mano ${state.activeHandIndex + 1}")
            
            // Verificar si la nueva mano tiene blackjack/21
            val nextHand = state.hands[state.activeHandIndex]
            if (nextHand.calculateValue() == 21) {
                state.statuses[state.activeHandIndex] = HandStatus.STANDING
                return handleHandComplete(playerId, chips)
            }
            
            return buildGameState(playerId, GamePhase.PLAYER_TURN, chips)
        } else {
            // Todas las manos completas - turno del dealer
            println("✅ Todas las manos completas - Turno del dealer")
            return playDealerTurn(playerId, chips)
        }
    }

    /**
     * Avanza a la siguiente mano disponible
     * @return true si hay más manos por jugar, false si todas están completas
     */
    private fun advanceToNextHand(playerId: String): Boolean {
        val state = playerStates[playerId] ?: return false
        
        // Buscar siguiente mano en estado WAITING
        for (i in (state.activeHandIndex + 1) until state.hands.size) {
            if (state.statuses[i] == HandStatus.WAITING) {
                state.activeHandIndex = i
                state.statuses[i] = HandStatus.PLAYING
                return true
            }
        }
        
        return false
    }

    /**
     * Turno del dealer
     */
    private fun playDealerTurn(playerId: String, chips: Int): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        
        // Revelar carta oculta
        dealerHand.revealAll()
        
        // Verificar si hay al menos una mano que no se pasó
        val hasActiveHand = state.statuses.any { it == HandStatus.STANDING || it == HandStatus.BLACKJACK }
        
        if (hasActiveHand) {
            // El dealer juega
            println("🎰 Dealer revela: ${dealerHand.getCards()} = ${dealerHand.calculateValue()}")
            
            while (shouldDealerHit()) {
                val card = deck.dealCard(hidden = false)
                dealerHand.addCard(card)
                println("🎰 Dealer pide: $card (Total: ${dealerHand.calculateValue()})")
            }
            
            if (dealerHand.isBusted()) {
                println("💥 Dealer se pasó con ${dealerHand.calculateValue()}")
            } else {
                println("✋ Dealer se planta con ${dealerHand.calculateValue()}")
            }
        } else {
            println("🎰 Dealer no juega (todas las manos se pasaron)")
        }
        
        return buildGameState(playerId, GamePhase.GAME_OVER, chips)
    }

    /**
     * Determina si el dealer debe pedir
     */
    private fun shouldDealerHit(): Boolean {
        val value = dealerHand.calculateValue()
        return if (gameSettings.dealerHitsOnSoft17) {
            // Dealer pide con soft 17
            value < 17 || (value == 17 && dealerHand.isSoft())
        } else {
            // Dealer se planta con cualquier 17
            value < 17
        }
    }

    /**
     * Obtiene el resultado del juego (para todas las manos)
     */
    fun getGameResult(playerId: String, baseBet: Int, playerChips: Int): ServerMessage.GameResult {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        
        val dealerValue = dealerHand.calculateValue()
        val dealerBusted = dealerHand.isBusted()
        val dealerBlackjack = dealerHand.isBlackjack()
        
        var totalPayout = 0
        val handResults = mutableListOf<SingleHandResult>()
        
        for (i in 0 until state.hands.size) {
            val hand = state.hands[i]
            val bet = state.bets[i]
            val status = state.statuses[i]
            val handValue = hand.calculateValue()
            
            val (result, payout) = when {
                status == HandStatus.BUSTED -> {
                    GameResultType.LOSE to -bet
                }
                hand.isBlackjack() && hand.getCards().size == 2 -> {
                    if (dealerBlackjack) {
                        GameResultType.PUSH to 0
                    } else {
                        GameResultType.BLACKJACK to (bet * gameSettings.blackjackPayout).toInt()
                    }
                }
                dealerBusted -> {
                    GameResultType.WIN to bet
                }
                dealerBlackjack -> {
                    GameResultType.LOSE to -bet
                }
                handValue > dealerValue -> {
                    GameResultType.WIN to bet
                }
                handValue < dealerValue -> {
                    GameResultType.LOSE to -bet
                }
                else -> {
                    GameResultType.PUSH to 0
                }
            }
            
            totalPayout += payout
            handResults.add(SingleHandResult(
                handIndex = i,
                cards = hand.getCards(),
                score = handValue,
                bet = bet,
                result = result,
                payout = payout
            ))
            
            println("   Mano ${i + 1}: $handValue vs Dealer $dealerValue → $result (${if (payout >= 0) "+$payout" else "$payout"})")
        }
        
        val newTotal = playerChips + totalPayout
        
        // Determinar resultado principal (para compatibilidad)
        val mainResult = when {
            handResults.all { it.result == GameResultType.LOSE || it.result == GameResultType.PUSH } -> {
                if (handResults.any { it.result == GameResultType.PUSH }) GameResultType.PUSH else GameResultType.LOSE
            }
            handResults.any { it.result == GameResultType.BLACKJACK } -> GameResultType.BLACKJACK
            handResults.any { it.result == GameResultType.WIN } -> GameResultType.WIN
            else -> GameResultType.PUSH
        }
        
        val message = if (state.numberOfHands > 1) {
            val wins = handResults.count { it.result == GameResultType.WIN || it.result == GameResultType.BLACKJACK }
            val losses = handResults.count { it.result == GameResultType.LOSE }
            "Ganaste $wins mano(s), perdiste $losses | Pago total: ${if (totalPayout >= 0) "+$totalPayout" else "$totalPayout"}"
        } else {
            buildResultMessage(mainResult, handResults[0].score, dealerValue)
        }
        
        println("💰 Resultado total: $totalPayout | Nuevo balance: $newTotal")
        
        return ServerMessage.GameResult(
            result = mainResult,
            playerFinalScore = handResults[0].score,
            dealerFinalScore = dealerValue,
            dealerFinalHand = dealerHand.getCards(),
            payout = totalPayout,
            newChipsTotal = newTotal,
            message = message,
            handResults = handResults
        )
    }

    /**
     * Construye resultado de rendición
     */
    private fun buildSurrenderResult(playerId: String, chips: Int): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        
        // Marcar todas las manos como completadas
        for (i in 0 until state.statuses.size) {
            state.statuses[i] = HandStatus.COMPLETED
        }
        
        return buildGameState(playerId, GamePhase.GAME_OVER, chips)
    }

    /**
     * Obtiene resultado de rendición
     */
    fun getSurrenderResult(playerId: String, bet: Int, playerChips: Int): ServerMessage.GameResult {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val hand = state.hands[0]
        
        val payout = -(bet / 2)
        val newTotal = playerChips + payout
        
        return ServerMessage.GameResult(
            result = GameResultType.SURRENDER,
            playerFinalScore = hand.calculateValue(),
            dealerFinalScore = dealerHand.calculateValue(),
            dealerFinalHand = dealerHand.getCards(),
            payout = payout,
            newChipsTotal = newTotal,
            message = "Te has rendido. Recuperas ${bet / 2} fichas.",
            handResults = listOf(
                SingleHandResult(0, hand.getCards(), hand.calculateValue(), bet, GameResultType.SURRENDER, payout)
            )
        )
    }

    /**
     * Construye el mensaje de resultado
     */
    private fun buildResultMessage(result: GameResultType, playerValue: Int, dealerValue: Int): String {
        return when (result) {
            GameResultType.BLACKJACK -> "¡BLACKJACK! Has ganado con 21 natural"
            GameResultType.WIN -> {
                if (dealerValue > 21) {
                    "¡Ganaste! El dealer se pasó ($dealerValue)"
                } else {
                    "¡Ganaste! $playerValue vs $dealerValue"
                }
            }
            GameResultType.LOSE -> {
                if (playerValue > 21) {
                    "Perdiste. Te pasaste ($playerValue)"
                } else {
                    "Perdiste. $playerValue vs $dealerValue"
                }
            }
            GameResultType.PUSH -> "Empate. Ambos tienen $playerValue"
            GameResultType.SURRENDER -> "Te has rendido."
        }
    }

    /**
     * Construye el estado del juego
     */
    private fun buildGameState(playerId: String, phase: GamePhase, playerChips: Int): ServerMessage.GameState {
        val state = playerStates[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        
        val activeHand = state.hands[state.activeHandIndex]
        val activeScore = activeHand.calculateValue()
        
        val dealerScore = if (phase == GamePhase.GAME_OVER) {
            dealerHand.calculateValue()
        } else {
            // Solo mostrar carta visible
            dealerHand.getVisibleCards().firstOrNull()?.rank?.value ?: 0
        }
        
        // Calcular probabilidad de pasarse
        val bustProbability = if (phase == GamePhase.PLAYER_TURN && activeScore < 21) {
            calculateBustProbability(activeScore)
        } else 0.0
        
        // Acciones disponibles
        val canHit = phase == GamePhase.PLAYER_TURN && !activeHand.isBusted() && activeScore < 21
        val canStand = phase == GamePhase.PLAYER_TURN && !activeHand.isBusted()
        val isOnSplitHand = state.numberOfHands > 1
        val canDouble = phase == GamePhase.PLAYER_TURN &&
                       activeHand.getCards().size == 2 &&
                       !activeHand.isBusted() &&
                       state.bets[state.activeHandIndex] <= playerChips &&
                       (!isOnSplitHand || gameSettings.allowDoubleAfterSplit)
        val canSplit = phase == GamePhase.PLAYER_TURN &&
                      state.numberOfHands < gameSettings.maxSplits + 1 &&
                      activeHand.getCards().size == 2 &&
                      activeHand.getCards()[0].rank.value == activeHand.getCards()[1].rank.value &&
                      state.bets[state.activeHandIndex] <= playerChips
        val canSurrender = phase == GamePhase.PLAYER_TURN &&
                          state.activeHandIndex == 0 &&
                          activeHand.getCards().size == 2 &&
                          gameSettings.allowSurrender
        
        // Construir estado de múltiples manos
        val multiHandStates = state.hands.mapIndexed { index, hand ->
            MultiHandState(
                handIndex = index,
                cards = hand.getCards(),
                score = hand.calculateValue(),
                bet = state.bets[index],
                status = state.statuses[index],
                canHit = index == state.activeHandIndex && canHit,
                canStand = index == state.activeHandIndex && canStand,
                canDouble = index == state.activeHandIndex && canDouble,
                canSplit = index == state.activeHandIndex && canSplit
            )
        }
        
        return ServerMessage.GameState(
            playerHand = activeHand.getCards(),
            dealerHand = dealerHand.getCards(),
            playerScore = activeScore,
            dealerScore = dealerScore,
            gameState = phase,
            canRequestCard = canHit,
            canStand = canStand,
            canDouble = canDouble,
            canSplit = canSplit,
            canSurrender = canSurrender,
            currentBet = state.bets[state.activeHandIndex],
            playerChips = playerChips,
            splitHand = null,
            splitScore = null,
            activeSplitHand = 0,
            bustProbability = bustProbability,
            multipleHands = multiHandStates,
            activeHandIndex = state.activeHandIndex,
            numberOfHands = state.numberOfHands,
            totalBet = state.bets.sum()
        )
    }

    /**
     * Calcula probabilidad de pasarse
     */
    private fun calculateBustProbability(currentValue: Int): Double {
        if (currentValue >= 21) return 1.0
        if (currentValue <= 11) return 0.0
        
        val safeCards = 21 - currentValue
        val bustingCards = 10 - safeCards
        return (bustingCards * 4.0) / 52.0
    }

    /**
     * Verifica y reinicia el mazo si es necesario
     */
    fun checkAndResetDeck() {
        if (deck.needsReset()) {
            println("🔄 Mazo bajo en cartas, reiniciando...")
            deck.reset()
            deck.shuffle()
        }
    }

    /**
     * Obtiene la mano del jugador (para historial)
     */
    fun getPlayerHand(playerId: String): List<Card> {
        val state = playerStates[playerId] ?: return emptyList()
        return state.hands.getOrNull(0)?.getCards() ?: emptyList()
    }

    /**
     * Estima las fichas del jugador
     */
    private fun estimatePlayerChips(playerId: String): Int {
        return GameConfig.INITIAL_CHIPS
    }

    /**
     * Obtiene el estado actual (para uso interno)
     */
    fun getCurrentState(playerId: String, playerChips: Int): ServerMessage.GameState {
        return buildGameState(playerId, GamePhase.PLAYER_TURN, playerChips)
    }

    /**
     * Selecciona una mano específica (para navegación manual entre manos)
     * Nota: El avance entre manos es automático, pero esto permite selección manual si se necesita
     */
    fun selectHand(playerId: String, handIndex: Int): Boolean {
        val state = playerStates[playerId] ?: return false
        
        // Solo se puede seleccionar una mano que esté en espera o jugando
        if (handIndex < 0 || handIndex >= state.hands.size) return false
        
        val status = state.statuses[handIndex]
        if (status != HandStatus.WAITING && status != HandStatus.PLAYING) return false
        
        state.activeHandIndex = handIndex
        if (status == HandStatus.WAITING) {
            state.statuses[handIndex] = HandStatus.PLAYING
        }
        
        return true
    }
}
