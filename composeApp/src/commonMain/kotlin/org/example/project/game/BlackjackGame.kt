package org.example.project.game

import org.example.project.model.Hand
import org.example.project.protocol.GameResultType

/**
 * Lógica del juego de Blackjack
 *
 * Esta clase contiene las reglas puras del juego,
 * sin dependencia de red ni UI.
 */
object BlackjackGame {

    /**
     * Determina si el dealer debe pedir otra carta
     *
     * Regla estándar de Blackjack:
     * - El dealer debe pedir si tiene 16 o menos
     * - El dealer debe plantarse si tiene 17 o más
     *
     * @param dealerHand Mano del dealer
     * @return true si el dealer debe pedir, false si debe plantarse
     */
    fun shouldDealerHit(dealerHand: Hand): Boolean {
        val dealerValue = dealerHand.calculateValue()
        return dealerValue < 17
    }

    /**
     * Determina el ganador de una partida
     *
     * @param playerHand Mano del jugador
     * @param dealerHand Mano del dealer
     * @return El resultado desde la perspectiva del jugador
     */
    fun determineWinner(playerHand: Hand, dealerHand: Hand): GameResultType {
        val playerValue = playerHand.calculateValue()
        val dealerValue = dealerHand.calculateValue()

        // Si el jugador se pasó, pierde automáticamente
        if (playerHand.isBusted()) {
            return GameResultType.LOSE
        }

        // Si el dealer se pasó, el jugador gana
        if (dealerHand.isBusted()) {
            return GameResultType.WIN
        }

        // Blackjack natural del jugador (gana salvo que dealer también tenga)
        if (playerHand.isBlackjack()) {
            return if (dealerHand.isBlackjack()) {
                GameResultType.PUSH  // Ambos tienen Blackjack
            } else {
                GameResultType.BLACKJACK  // Solo el jugador tiene Blackjack
            }
        }

        // Blackjack natural solo del dealer
        if (dealerHand.isBlackjack()) {
            return GameResultType.LOSE
        }

        // Comparar valores
        return when {
            playerValue > dealerValue -> GameResultType.WIN
            playerValue < dealerValue -> GameResultType.LOSE
            else -> GameResultType.PUSH  // Empate
        }
    }

    /**
     * Genera un mensaje descriptivo del resultado
     *
     * @param result Tipo de resultado
     * @param playerValue Valor de la mano del jugador
     * @param dealerValue Valor de la mano del dealer
     * @return Mensaje descriptivo
     */
    fun getResultMessage(
        result: GameResultType,
        playerValue: Int,
        dealerValue: Int
    ): String {
        return when (result) {
            GameResultType.BLACKJACK -> "¡BLACKJACK! Has ganado con $playerValue (Blackjack natural)"
            GameResultType.WIN -> {
                if (dealerValue > 21) {
                    "¡Has ganado! El dealer se pasó con $dealerValue (Tú: $playerValue)"
                } else {
                    "¡Has ganado! $playerValue vs $dealerValue"
                }
            }
            GameResultType.LOSE -> {
                if (playerValue > 21) {
                    "Has perdido. Te pasaste con $playerValue"
                } else {
                    "Has perdido. $playerValue vs $dealerValue"
                }
            }
            GameResultType.PUSH -> "Empate. Ambos tenéis $playerValue"
            GameResultType.SURRENDER -> "Te has rendido. Recuperas la mitad de tu apuesta"
        }
    }

    /**
     * Verifica si el jugador puede seguir pidiendo cartas
     *
     * @param playerHand Mano del jugador
     * @return true si puede pedir, false si no
     */
    fun canPlayerHit(playerHand: Hand): Boolean {
        return !playerHand.isBusted() && !playerHand.isBlackjack()
    }

    /**
     * Verifica si el jugador puede plantarse
     *
     * @param playerHand Mano del jugador
     * @return true si puede plantarse, false si no
     */
    fun canPlayerStand(playerHand: Hand): Boolean {
        // El jugador puede plantarse si tiene al menos 2 cartas y no se ha pasado
        return playerHand.size() >= 2 && !playerHand.isBusted()
    }

    /**
     * Calcula las estadísticas de la partida
     *
     * @param results Lista de resultados de partidas
     * @return Mapa con estadísticas (wins, losses, blackjacks, etc.)
     */
    fun calculateStats(results: List<GameResultType>): Map<String, Int> {
        var wins = 0
        var losses = 0
        var pushes = 0
        var blackjacks = 0

        for (result in results) {
            when (result) {
                GameResultType.WIN -> wins++
                GameResultType.LOSE -> losses++
                GameResultType.PUSH -> pushes++
                GameResultType.BLACKJACK -> {
                    wins++
                    blackjacks++
                }
                GameResultType.SURRENDER -> losses++
            }
        }

        return mapOf(
            "wins" to wins,
            "losses" to losses,
            "pushes" to pushes,
            "blackjacks" to blackjacks,
            "total" to results.size
        )
    }
}
