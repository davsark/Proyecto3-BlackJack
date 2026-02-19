package org.example.project.model

import org.example.project.protocol.Card
import org.example.project.protocol.Rank

/**
 * Representa la mano de un jugador en Blackjack
 */
class Hand {
    private val cards = mutableListOf<Card>()

    /**
     * Añade una carta a la mano
     */
    fun addCard(card: Card) {
        cards.add(card)
    }

    /**
     * Elimina la última carta de la mano (para split)
     */
    fun removeLastCard(): Card? {
        return if (cards.isNotEmpty()) cards.removeLast() else null
    }

    /**
     * Obtiene todas las cartas de la mano
     */
    fun getCards(): List<Card> = cards.toList()

    /**
     * Limpia todas las cartas de la mano
     */
    fun clear() {
        cards.clear()
    }

    /**
     * Calcula el mejor valor posible de la mano
     */
    fun calculateValue(): Int {
        var total = 0
        var aceCount = 0

        for (card in cards) {
            if (!card.hidden) {
                if (card.rank == Rank.ACE) {
                    aceCount++
                    total += 1
                } else {
                    total += card.rank.value
                }
            }
        }

        while (aceCount > 0 && total + 10 <= 21) {
            total += 10
            aceCount--
        }

        return total
    }

    /**
     * Verifica si la mano es "soft" (tiene un As contando como 11)
     */
    fun isSoft(): Boolean {
        var total = 0
        var aceCount = 0

        for (card in cards) {
            if (!card.hidden) {
                if (card.rank == Rank.ACE) {
                    aceCount++
                    total += 1
                } else {
                    total += card.rank.value
                }
            }
        }

        // Si podemos contar un As como 11 sin pasarnos, es soft
        return aceCount > 0 && total + 10 <= 21
    }

    /**
     * Verifica si la mano es un Blackjack natural
     */
    fun isBlackjack(): Boolean {
        if (cards.size != 2) return false
        val hasAce = cards.any { !it.hidden && it.rank == Rank.ACE }
        val hasTen = cards.any { !it.hidden && it.rank.value == 10 }
        return hasAce && hasTen
    }

    /**
     * Verifica si la mano se ha pasado de 21
     */
    fun isBusted(): Boolean = calculateValue() > 21

    /**
     * Verifica si se puede dividir (dos cartas del mismo valor)
     */
    fun canSplit(): Boolean {
        if (cards.size != 2) return false
        return cards[0].rank.value == cards[1].rank.value
    }

    /**
     * Revela todas las cartas ocultas
     */
    fun revealAll() {
        for (i in cards.indices) {
            cards[i] = cards[i].copy(hidden = false)
        }
    }

    fun size(): Int = cards.size
    fun isEmpty(): Boolean = cards.isEmpty()

    /**
     * Obtiene solo las cartas visibles (no ocultas)
     */
    fun getVisibleCards(): List<Card> = cards.filter { !it.hidden }

    /**
     * Descripción de la mano para records
     */
    fun getDescription(): String {
        val value = calculateValue()
        val cardCount = cards.size
        return when {
            isBlackjack() -> "Blackjack"
            value == 21 && cardCount >= 5 -> "$cardCount cartas sin pasarse (21)"
            value == 21 -> "21 con $cardCount cartas"
            else -> "$value con $cardCount cartas"
        }
    }

    override fun toString(): String {
        val cardsStr = cards.joinToString(", ")
        val value = if (cards.all { !it.hidden }) calculateValue().toString() else "?"
        return "Hand(cartas: [$cardsStr], valor: $value)"
    }
}
