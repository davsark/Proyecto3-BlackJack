package org.example.project.server

import org.example.project.model.Deck
import org.example.project.protocol.GamePhase
import org.example.project.protocol.GameSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DealerAITest {

    // Creamos una configuración base para los tests
    private val defaultSettings = GameSettings(
        numberOfDecks = 1,
        initialChips = 1000,
        minBet = 10,
        maxBet = 500,
        blackjackPayout = 1.5,
        dealerHitsOnSoft17 = false,
        allowDoubleAfterSplit = true,
        allowSurrender = true,
        maxSplits = 3
    )

    @Test
    fun `test startNewGame reparte correctamente las cartas iniciales`() {
        val deck = Deck(1)
        val dealerAI = DealerAI(deck, defaultSettings)
        val playerId = "jugador_1"

        val gameState = dealerAI.startNewGame(playerId, bet = 50, playerChips = 1000, numberOfHands = 1)

        // El jugador debe tener 2 cartas
        assertEquals(2, gameState.playerHand.size, "El jugador debería recibir 2 cartas al inicio")
        // El dealer debe tener 2 cartas (una visible que va en el payload, y sabemos que tiene otra oculta internamente)
        assertEquals(2, gameState.dealerHand.size, "El dealer debería tener 2 cartas iniciales")

        // Verificamos que se haya registrado 1 mano
        assertEquals(1, gameState.numberOfHands)
    }

    @Test
    fun `test pedir carta (hit) anade una carta a la mano`() {
        val deck = Deck(1)
        val dealerAI = DealerAI(deck, defaultSettings)
        val playerId = "jugador_1"

        var gameState = dealerAI.startNewGame(playerId, bet = 50, playerChips = 1000, numberOfHands = 1)

        // Protegemos el test: Si de casualidad salió un Blackjack natural, el turno ya terminó.
        // Solo probamos el Hit si el jugador sigue en su turno.
        if (gameState.gameState == GamePhase.PLAYER_TURN) {
            val initialSize = gameState.playerHand.size
            gameState = dealerAI.playerHit(playerId)

            assertEquals(initialSize + 1, gameState.playerHand.size, "Pedir carta debería aumentar en 1 el tamaño de la mano")
        }
    }

    @Test
    fun `test plantarse (stand) con una mano pasa el turno al dealer y termina`() {
        val deck = Deck(1)
        val dealerAI = DealerAI(deck, defaultSettings)
        val playerId = "jugador_1"

        val initialState = dealerAI.startNewGame(playerId, bet = 50, playerChips = 1000, numberOfHands = 1)

        if (initialState.gameState == GamePhase.PLAYER_TURN) {
            val finalState = dealerAI.playerStand(playerId)

            // Al plantarse con una sola mano, la IA debe jugar el turno del dealer y marcar GAME_OVER
            assertEquals(GamePhase.GAME_OVER, finalState.gameState, "Plantarse debe terminar el juego y pasar al dealer")
        }
    }

    @Test
    fun `test rendirse (surrender) devuelve la mitad de la apuesta correctamente`() {
        val deck = Deck(1)
        val dealerAI = DealerAI(deck, defaultSettings)
        val playerId = "jugador_1"
        val initialChips = 1000
        val bet = 100

        val initialState = dealerAI.startNewGame(playerId, bet, initialChips, 1)

        if (initialState.gameState == GamePhase.PLAYER_TURN) {
            // Ejecutamos la acción de rendición
            val surrenderState = dealerAI.playerSurrender(playerId)

            assertNotNull(surrenderState, "Se debe permitir la rendición con 2 cartas iniciales")
            assertEquals(GamePhase.GAME_OVER, surrenderState?.gameState, "Al rendirse, el juego debe terminar")

            // Verificamos las matemáticas del pago
            val result = dealerAI.getSurrenderResult(playerId, bet, initialChips)
            assertEquals(-50, result.payout, "Rendirse con apuesta de 100 debe resultar en perder 50 fichas")
            assertEquals(950, result.newChipsTotal, "Las fichas deben bajar de 1000 a 950 tras rendirse")
        }
    }
}