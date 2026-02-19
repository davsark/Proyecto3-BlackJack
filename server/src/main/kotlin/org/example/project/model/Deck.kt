package org.example.project.model

import org.example.project.config.GameConfig
import org.example.project.protocol.Card
import org.example.project.protocol.Rank
import org.example.project.protocol.Suit

/**
 * Representa una baraja de cartas (soporta múltiples mazos)
 */
class Deck(private val numberOfDecks: Int = 1) {
    private val cards = mutableListOf<Card>()

    init {
        reset()
    }

    /**
     * Resetea la baraja con todas las cartas (52 * numberOfDecks)
     */
    fun reset() {
        cards.clear()
        repeat(numberOfDecks) {
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    cards.add(Card(rank, suit, hidden = false))
                }
            }
        }
    }

    /**
     * Baraja las cartas aleatoriamente
     */
    fun shuffle() {
        cards.shuffle()
    }

    /**
     * Reparte una carta de la parte superior
     * @param hidden Si la carta debe estar oculta (para el dealer)
     * @return La carta repartida
     * @throws IllegalStateException si no quedan cartas
     */
    fun dealCard(hidden: Boolean = false): Card {
        if (cards.isEmpty()) {
            // Auto-reset si se agotan las cartas
            reset()
            shuffle()
        }
        val card = cards.removeFirst()
        return card.copy(hidden = hidden)
    }

    /**
     * Número de cartas restantes en la baraja
     */
    fun remainingCards(): Int = cards.size

    /**
     * Verifica si la baraja necesita ser reemplazada
     */
    fun needsReset(): Boolean = cards.size < GameConfig.DECK_RESET_THRESHOLD

    /**
     * Total de cartas en un mazo completo
     */
    fun totalCards(): Int = 52 * numberOfDecks

    override fun toString(): String {
        return "Deck(mazos: $numberOfDecks, cartas restantes: ${cards.size})"
    }
}
