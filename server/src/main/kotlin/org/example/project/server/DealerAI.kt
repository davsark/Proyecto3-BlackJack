package org.example.project.server

import org.example.project.config.GameConfig
import org.example.project.game.BlackjackGame
import org.example.project.model.Deck
import org.example.project.model.Hand
import org.example.project.protocol.*

/**
 * Inteligencia Artificial del Dealer para modo PVE y PVP
 * Implementa las reglas estándar del Blackjack con soporte para apuestas y múltiples manos
 */
class DealerAI(
    private val deck: Deck,
    private val gameSettings: GameSettings
) {
    private val dealerHand = Hand()
    private val playerHands = mutableMapOf<String, Hand>()
    private val splitHands = mutableMapOf<String, Hand>()
    private val playerBets = mutableMapOf<String, Int>()
    private val hasDoubled = mutableMapOf<String, Boolean>()
    private val hasSplit = mutableMapOf<String, Boolean>()
    private val activeSplitHand = mutableMapOf<String, Int>() // 0 = main, 1 = split
    
    // Soporte para múltiples manos
    private val multipleHands = mutableMapOf<String, MutableList<Hand>>()
    private val multipleHandsBets = mutableMapOf<String, MutableList<Int>>()
    private val multipleHandsStatus = mutableMapOf<String, MutableList<HandStatus>>()
    private val activeHandIndex = mutableMapOf<String, Int>()
    private val numberOfHandsPerPlayer = mutableMapOf<String, Int>()

    /**
     * Inicia una nueva partida (soporta múltiples manos)
     * @param numberOfHands Número de manos a jugar (1-3)
     */
    fun startNewGame(playerId: String, bet: Int, playerChips: Int, numberOfHands: Int = 1): ServerMessage.GameState {
        // Limpiar manos anteriores
        dealerHand.clear()
        playerHands.remove(playerId)
        splitHands.remove(playerId)
        playerBets[playerId] = bet
        hasDoubled[playerId] = false
        hasSplit[playerId] = false
        activeSplitHand[playerId] = 0
        
        // Configurar múltiples manos
        numberOfHandsPerPlayer[playerId] = numberOfHands
        activeHandIndex[playerId] = 0
        
        if (numberOfHands > 1) {
            // Modo múltiples manos
            val hands = mutableListOf<Hand>()
            val bets = mutableListOf<Int>()
            val statuses = mutableListOf<HandStatus>()
            
            for (i in 0 until numberOfHands) {
                hands.add(Hand())
                bets.add(bet)
                statuses.add(if (i == 0) HandStatus.PLAYING else HandStatus.WAITING)
            }
            
            multipleHands[playerId] = hands
            multipleHandsBets[playerId] = bets
            multipleHandsStatus[playerId] = statuses
            
            // Repartir cartas
            for (hand in hands) {
                hand.addCard(deck.dealCard(hidden = false))
            }
            dealerHand.addCard(deck.dealCard(hidden = false))
            for (hand in hands) {
                hand.addCard(deck.dealCard(hidden = false))
            }
            dealerHand.addCard(deck.dealCard(hidden = true))
            
            playerHands[playerId] = hands[0]
            
            println("🎴 Nueva partida multi-mano iniciada ($numberOfHands manos, Apuesta: $bet cada una)")
            for ((index, hand) in hands.withIndex()) {
                println("   Mano ${index + 1}: ${hand.getCards()} = ${hand.calculateValue()}")
            }
            println("   Dealer: ${dealerHand.getCards()}")
            
        } else {
            // Modo mano única
            multipleHands.remove(playerId)
            multipleHandsBets.remove(playerId)
            multipleHandsStatus.remove(playerId)
            
            val playerHand = Hand()
            playerHands[playerId] = playerHand

            playerHand.addCard(deck.dealCard(hidden = false))
            dealerHand.addCard(deck.dealCard(hidden = false))
            playerHand.addCard(deck.dealCard(hidden = false))
            dealerHand.addCard(deck.dealCard(hidden = true))

            println("🎴 Nueva partida iniciada (Apuesta: $bet)")
            println("   Jugador: ${playerHand.getCards()} = ${playerHand.calculateValue()}")
            println("   Dealer: ${dealerHand.getCards()}")
        }

        return buildGameState(playerId, GamePhase.PLAYER_TURN, playerChips)
    }

    /**
     * Procesa la petición de carta del jugador
     */
    fun playerHit(playerId: String): ServerMessage.GameState {
        val hand = getActiveHand(playerId) ?: throw IllegalStateException("Jugador no encontrado")
        val chips = estimatePlayerChips(playerId)

        // Repartir carta al jugador
        val newCard = deck.dealCard(hidden = false)
        hand.addCard(newCard)

        println("🃏 Jugador pide carta: $newCard (Total: ${hand.calculateValue()})")

        // Verificar si el jugador se pasó
        return if (hand.isBusted()) {
            println("💥 ¡Jugador se pasó! (${hand.calculateValue()})")
            
            // Si tiene split y está en mano principal, pasar a split
            if (hasSplit[playerId] == true && activeSplitHand[playerId] == 0) {
                activeSplitHand[playerId] = 1
                buildGameState(playerId, GamePhase.PLAYER_TURN, chips)
            } else {
                dealerHand.revealAll()
                buildGameState(playerId, GamePhase.GAME_OVER, chips)
            }
        } else if (hand.calculateValue() == 21) {
            println("🎯 Jugador alcanza 21")
            
            if (hasSplit[playerId] == true && activeSplitHand[playerId] == 0) {
                activeSplitHand[playerId] = 1
                buildGameState(playerId, GamePhase.PLAYER_TURN, chips)
            } else {
                playDealerTurn(playerId)
            }
        } else {
            buildGameState(playerId, GamePhase.PLAYER_TURN, chips)
        }
    }

    /**
     * Procesa cuando el jugador se planta
     */
    fun playerStand(playerId: String): ServerMessage.GameState {
        val hand = getActiveHand(playerId) ?: throw IllegalStateException("Jugador no encontrado")
        println("✋ Jugador se planta con ${hand.calculateValue()}")
        
        // Si tiene split y está en mano principal, pasar a split
        if (hasSplit[playerId] == true && activeSplitHand[playerId] == 0) {
            activeSplitHand[playerId] = 1
            return buildGameState(playerId, GamePhase.PLAYER_TURN, estimatePlayerChips(playerId))
        }
        
        return playDealerTurn(playerId)
    }

    /**
     * Procesa cuando el jugador dobla
     */
    fun playerDouble(playerId: String): ServerMessage.GameState? {
        val hand = getActiveHand(playerId) ?: return null
        
        // Solo se puede doblar con 2 cartas
        if (hand.getCards().size != 2) return null
        
        // Dar una carta y plantarse
        val newCard = deck.dealCard(hidden = false)
        hand.addCard(newCard)
        hasDoubled[playerId] = true
        
        // Doblar la apuesta
        val currentBet = playerBets[playerId] ?: 0
        playerBets[playerId] = currentBet * 2
        
        println("🎲 Jugador dobla: $newCard (Total: ${hand.calculateValue()})")
        
        // Si tiene split, manejar
        if (hasSplit[playerId] == true && activeSplitHand[playerId] == 0) {
            activeSplitHand[playerId] = 1
            return buildGameState(playerId, GamePhase.PLAYER_TURN, estimatePlayerChips(playerId))
        }
        
        return playDealerTurn(playerId)
    }

    /**
     * Procesa cuando el jugador divide
     */
    fun playerSplit(playerId: String): ServerMessage.GameState? {
        val hand = playerHands[playerId] ?: return null
        val cards = hand.getCards()
        
        // Verificar que puede dividir
        if (cards.size != 2) return null
        if (cards[0].rank.value != cards[1].rank.value) return null
        if (hasSplit[playerId] == true) return null // Ya dividió
        
        // Crear mano de split
        val splitHand = Hand()
        val secondCard = cards[1]
        hand.removeLastCard()
        splitHand.addCard(secondCard)
        
        // Dar una carta a cada mano
        hand.addCard(deck.dealCard(hidden = false))
        splitHand.addCard(deck.dealCard(hidden = false))
        
        splitHands[playerId] = splitHand
        hasSplit[playerId] = true
        activeSplitHand[playerId] = 0
        
        println("✂️ Jugador divide: Mano 1: ${hand.getCards()}, Mano 2: ${splitHand.getCards()}")
        
        return buildGameState(playerId, GamePhase.PLAYER_TURN, estimatePlayerChips(playerId))
    }

    /**
     * Procesa cuando el jugador se rinde
     */
    fun playerSurrender(playerId: String): ServerMessage.GameResult? {
        val hand = playerHands[playerId] ?: return null
        
        // Solo se puede rendir con 2 cartas en mano principal
        if (hand.getCards().size != 2) return null
        if (hasSplit[playerId] == true) return null
        
        dealerHand.revealAll()
        val bet = playerBets[playerId] ?: 0
        
        println("🏳️ Jugador se rinde")
        
        return ServerMessage.GameResult(
            result = GameResultType.SURRENDER,
            playerFinalScore = hand.calculateValue(),
            dealerFinalScore = dealerHand.calculateValue(),
            message = "Te has rendido. Recuperas la mitad de tu apuesta.",
            dealerFinalHand = dealerHand.getCards(),
            payout = -(bet / 2),
            newChipsTotal = 0 // Se calcula en ClientHandler
        )
    }

    /**
     * El dealer juega su turno
     */
    private fun playDealerTurn(playerId: String): ServerMessage.GameState {
        // Revelar la carta oculta del dealer
        dealerHand.revealAll()
        println("👁️ Dealer revela su mano: ${dealerHand.getCards()} = ${dealerHand.calculateValue()}")

        // El dealer pide cartas según las reglas
        while (shouldDealerHit()) {
            val newCard = deck.dealCard(hidden = false)
            dealerHand.addCard(newCard)
            println("🎴 Dealer pide carta: $newCard (Total: ${dealerHand.calculateValue()})")
        }

        if (dealerHand.isBusted()) {
            println("💥 ¡Dealer se pasó! (${dealerHand.calculateValue()})")
        } else {
            println("✋ Dealer se planta con ${dealerHand.calculateValue()}")
        }

        return buildGameState(playerId, GamePhase.GAME_OVER, estimatePlayerChips(playerId))
    }

    /**
     * Determina si el dealer debe pedir carta
     */
    private fun shouldDealerHit(): Boolean {
        val value = dealerHand.calculateValue()
        if (value < 17) return true
        if (value == 17 && gameSettings.dealerHitsOnSoft17 && dealerHand.isSoft()) return true
        return false
    }

    /**
     * Obtiene el resultado final del juego
     */
    fun getGameResult(playerId: String, bet: Int, previousChips: Int): ServerMessage.GameResult {
        val playerHand = playerHands[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val splitHand = splitHands[playerId]

        val result = BlackjackGame.determineWinner(playerHand, dealerHand)
        val playerScore = playerHand.calculateValue()
        val dealerScore = dealerHand.calculateValue()
        
        // Calcular pago
        val actualBet = if (hasDoubled[playerId] == true) bet * 2 else bet
        val payout = calculatePayout(result, actualBet, playerHand)
        
        // Calcular resultado del split si existe
        var splitResult: GameResultType? = null
        var splitPayout: Int? = null
        if (splitHand != null) {
            splitResult = BlackjackGame.determineWinner(splitHand, dealerHand)
            splitPayout = calculatePayout(splitResult, bet, splitHand)
        }
        
        val totalPayout = payout + (splitPayout ?: 0)
        val newChips = previousChips + actualBet + totalPayout + (if (splitHand != null) bet else 0)
        
        val message = BlackjackGame.getResultMessage(result, playerScore, dealerScore)

        println("🏆 Resultado: $result | Pago: $payout | Nuevas fichas: $newChips")

        return ServerMessage.GameResult(
            result = result,
            playerFinalScore = playerScore,
            dealerFinalScore = dealerScore,
            message = message,
            dealerFinalHand = dealerHand.getCards(),
            payout = totalPayout,
            newChipsTotal = newChips,
            splitResult = splitResult,
            splitPayout = splitPayout
        )
    }

    /**
     * Calcula el pago basado en el resultado
     */
    private fun calculatePayout(result: GameResultType, bet: Int, hand: Hand): Int {
        return when (result) {
            GameResultType.BLACKJACK -> {
                // Blackjack natural paga 3:2 (o según configuración)
                (bet * gameSettings.blackjackPayout).toInt()
            }
            GameResultType.WIN -> bet // Gana 1:1
            GameResultType.PUSH -> 0 // Empate, recupera apuesta
            GameResultType.LOSE -> -bet // Pierde la apuesta
            GameResultType.SURRENDER -> -(bet / 2) // Pierde mitad
        }
    }

    /**
     * Construye el estado actual del juego (con soporte para múltiples manos)
     */
    private fun buildGameState(playerId: String, phase: GamePhase, playerChips: Int): ServerMessage.GameState {
        val playerHand = playerHands[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val splitHand = splitHands[playerId]
        val currentBet = playerBets[playerId] ?: 0
        val activeHand = getActiveHand(playerId) ?: playerHand
        val numHands = numberOfHandsPerPlayer[playerId] ?: 1

        val playerScore = activeHand.calculateValue()
        val dealerScore = if (phase == GamePhase.GAME_OVER) {
            dealerHand.calculateValue()
        } else {
            // Solo mostrar la carta visible del dealer
            dealerHand.getCards().firstOrNull { !it.hidden }?.rank?.value ?: 0
        }

        // Calcular probabilidad de pasarse
        val bustProbability = if (phase == GamePhase.PLAYER_TURN) {
            calculateBustProbability(activeHand.calculateValue())
        } else 0.0

        // Determinar acciones disponibles
        val canDouble = phase == GamePhase.PLAYER_TURN && 
                       activeHand.getCards().size == 2 && 
                       !activeHand.isBusted() &&
                       (gameSettings.allowDoubleAfterSplit || hasSplit[playerId] != true)
        
        val canSplit = phase == GamePhase.PLAYER_TURN &&
                      playerHand.getCards().size == 2 &&
                      playerHand.getCards()[0].rank.value == playerHand.getCards()[1].rank.value &&
                      hasSplit[playerId] != true

        val canSurrender = phase == GamePhase.PLAYER_TURN &&
                          playerHand.getCards().size == 2 &&
                          hasSplit[playerId] != true &&
                          gameSettings.allowSurrender
        
        // Construir estados de múltiples manos
        val multiHandStates = if (numHands > 1) buildMultiHandStates(playerId) else emptyList()
        val totalBetAmount = if (numHands > 1) currentBet * numHands else currentBet

        return ServerMessage.GameState(
            playerHand = playerHand.getCards(),
            dealerHand = dealerHand.getCards(),
            playerScore = playerScore,
            dealerScore = dealerScore,
            gameState = phase,
            canRequestCard = phase == GamePhase.PLAYER_TURN && !activeHand.isBusted(),
            canStand = phase == GamePhase.PLAYER_TURN && !activeHand.isBusted(),
            canDouble = canDouble,
            canSplit = canSplit,
            canSurrender = canSurrender,
            currentBet = currentBet,
            playerChips = playerChips,
            splitHand = splitHand?.getCards(),
            splitScore = splitHand?.calculateValue(),
            activeSplitHand = activeSplitHand[playerId] ?: 0,
            bustProbability = bustProbability,
            multipleHands = multiHandStates,
            activeHandIndex = activeHandIndex[playerId] ?: 0,
            numberOfHands = numHands,
            totalBet = totalBetAmount
        )
    }

    /**
     * Obtiene la mano activa (principal o split)
     */
    private fun getActiveHand(playerId: String): Hand? {
        return if (activeSplitHand[playerId] == 1) {
            splitHands[playerId]
        } else {
            playerHands[playerId]
        }
    }

    /**
     * Estima las fichas del jugador
     */
    private fun estimatePlayerChips(playerId: String): Int {
        // Esto es una estimación, el valor real se maneja en ClientHandler
        return GameConfig.INITIAL_CHIPS
    }

    /**
     * Calcula la probabilidad de pasarse
     */
    private fun calculateBustProbability(currentValue: Int): Double {
        if (currentValue >= 21) return 1.0
        if (currentValue <= 11) return 0.0
        
        val bustingCards = when {
            currentValue == 12 -> 4 * 4 // Solo 10, J, Q, K nos pasan
            currentValue == 13 -> 4 * 5 // 9, 10, J, Q, K
            currentValue == 14 -> 4 * 6
            currentValue == 15 -> 4 * 7
            currentValue == 16 -> 4 * 8
            currentValue == 17 -> 4 * 9
            currentValue == 18 -> 4 * 10
            currentValue == 19 -> 4 * 10
            currentValue == 20 -> 4 * 10
            else -> 0
        }
        return bustingCards.toDouble() / 52.0
    }

    /**
     * Reinicia la baraja si es necesario
     */
    fun checkAndResetDeck() {
        if (deck.needsReset()) {
            println("🔄 Baraja baja en cartas, reiniciando...")
            deck.reset()
            deck.shuffle()
        }
    }
    
    /**
     * Selecciona una mano específica para jugar (modo multi-mano)
     */
    fun selectHand(playerId: String, handIndex: Int): Boolean {
        val hands = multipleHands[playerId] ?: return false
        val statuses = multipleHandsStatus[playerId] ?: return false
        
        if (handIndex < 0 || handIndex >= hands.size) return false
        if (statuses[handIndex] != HandStatus.WAITING) return false
        
        // Marcar la mano actual como completada si estaba jugando
        val currentIndex = activeHandIndex[playerId] ?: 0
        if (statuses[currentIndex] == HandStatus.PLAYING) {
            statuses[currentIndex] = HandStatus.STANDING
        }
        
        // Activar la nueva mano
        activeHandIndex[playerId] = handIndex
        statuses[handIndex] = HandStatus.PLAYING
        playerHands[playerId] = hands[handIndex]
        
        return true
    }
    
    /**
     * Obtiene el estado actual del juego
     */
    fun getCurrentState(playerId: String, playerChips: Int): ServerMessage.GameState {
        return buildGameState(playerId, GamePhase.PLAYER_TURN, playerChips)
    }
    
    /**
     * Obtiene la mano del jugador (para historial)
     */
    fun getPlayerHand(playerId: String): List<Card> {
        return playerHands[playerId]?.getCards() ?: emptyList()
    }
    
    /**
     * Avanza a la siguiente mano en modo multi-mano
     * @return true si hay más manos por jugar, false si se completaron todas
     */
    private fun advanceToNextHand(playerId: String): Boolean {
        val hands = multipleHands[playerId] ?: return false
        val statuses = multipleHandsStatus[playerId] ?: return false
        val currentIndex = activeHandIndex[playerId] ?: 0
        
        // Marcar la mano actual como completada
        if (statuses[currentIndex] == HandStatus.PLAYING) {
            statuses[currentIndex] = HandStatus.STANDING
        }
        
        // Buscar la siguiente mano pendiente
        for (i in (currentIndex + 1) until hands.size) {
            if (statuses[i] == HandStatus.WAITING) {
                activeHandIndex[playerId] = i
                statuses[i] = HandStatus.PLAYING
                playerHands[playerId] = hands[i]
                return true
            }
        }
        
        return false
    }
    
    /**
     * Verifica si todas las manos están completadas
     */
    private fun allHandsCompleted(playerId: String): Boolean {
        val statuses = multipleHandsStatus[playerId] ?: return true
        return statuses.all { it != HandStatus.WAITING && it != HandStatus.PLAYING }
    }
    
    /**
     * Construye los estados de múltiples manos para el mensaje
     */
    private fun buildMultiHandStates(playerId: String): List<MultiHandState> {
        val hands = multipleHands[playerId] ?: return emptyList()
        val bets = multipleHandsBets[playerId] ?: return emptyList()
        val statuses = multipleHandsStatus[playerId] ?: return emptyList()
        val currentActive = activeHandIndex[playerId] ?: 0
        
        return hands.mapIndexed { index, hand ->
            val isActive = index == currentActive && statuses[index] == HandStatus.PLAYING
            MultiHandState(
                handIndex = index,
                cards = hand.getCards(),
                score = hand.calculateValue(),
                bet = bets[index],
                status = statuses[index],
                canHit = isActive && !hand.isBusted() && hand.calculateValue() < 21,
                canStand = isActive && !hand.isBusted(),
                canDouble = isActive && hand.getCards().size == 2 && !hand.isBusted(),
                canSplit = isActive && hand.getCards().size == 2 && 
                          hand.getCards()[0].rank.value == hand.getCards()[1].rank.value
            )
        }
    }
}
