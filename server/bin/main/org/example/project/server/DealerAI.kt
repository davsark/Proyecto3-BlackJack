package org.example.project.server

import org.example.project.config.GameConfig
import org.example.project.game.BlackjackGame
import org.example.project.model.Deck
import org.example.project.model.Hand
import org.example.project.protocol.*

/**
 * Inteligencia Artificial del Dealer para modo PVE y PVP
 * Implementa las reglas estándar del Blackjack con soporte para apuestas
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

    /**
     * Inicia una nueva partida
     */
    fun startNewGame(playerId: String, bet: Int, playerChips: Int): ServerMessage.GameState {
        // Limpiar manos anteriores
        dealerHand.clear()
        playerHands.remove(playerId)
        splitHands.remove(playerId)
        playerBets[playerId] = bet
        hasDoubled[playerId] = false
        hasSplit[playerId] = false
        activeSplitHand[playerId] = 0

        // Crear mano para el jugador
        val playerHand = Hand()
        playerHands[playerId] = playerHand

        // Repartir cartas iniciales (2 para jugador, 2 para dealer)
        playerHand.addCard(deck.dealCard(hidden = false))
        dealerHand.addCard(deck.dealCard(hidden = false))
        playerHand.addCard(deck.dealCard(hidden = false))
        dealerHand.addCard(deck.dealCard(hidden = true)) // Segunda carta del dealer oculta

        println("🎴 Nueva partida iniciada (Apuesta: $bet)")
        println("   Jugador: ${playerHand.getCards()} = ${playerHand.calculateValue()}")
        println("   Dealer: ${dealerHand.getCards()}")

        return buildGameState(playerId, GamePhase.PLAYER_TURN, playerChips - bet)
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
     * Construye el estado actual del juego
     */
    private fun buildGameState(playerId: String, phase: GamePhase, playerChips: Int): ServerMessage.GameState {
        val playerHand = playerHands[playerId] ?: throw IllegalStateException("Jugador no encontrado")
        val splitHand = splitHands[playerId]
        val currentBet = playerBets[playerId] ?: 0
        val activeHand = getActiveHand(playerId) ?: playerHand

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
                       playerChips >= currentBet && // Verificar que tiene fichas suficientes
                       (gameSettings.allowDoubleAfterSplit || hasSplit[playerId] != true)
        
        val canSplit = phase == GamePhase.PLAYER_TURN &&
                      playerHand.getCards().size == 2 &&
                      playerHand.getCards()[0].rank.value == playerHand.getCards()[1].rank.value &&
                      hasSplit[playerId] != true

        val canSurrender = phase == GamePhase.PLAYER_TURN &&
                          playerHand.getCards().size == 2 &&
                          hasSplit[playerId] != true &&
                          gameSettings.allowSurrender

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
            bustProbability = bustProbability
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
}
