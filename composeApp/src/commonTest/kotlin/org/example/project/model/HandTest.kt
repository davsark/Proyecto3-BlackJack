package org.example.project.model

import org.example.project.protocol.Card
import org.example.project.protocol.Rank
import org.example.project.protocol.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandTest {

    @Test
    fun `test calculo de valor simple sin ases`() {
        val hand = Hand()
        hand.addCard(Card(Rank.TEN, Suit.HEARTS))
        hand.addCard(Card(Rank.FIVE, Suit.SPADES))

        // 10 + 5 = 15
        assertEquals(15, hand.calculateValue(), "El valor debería ser 15")
        assertFalse(hand.isBusted(), "Con 15 no debería estar pasado")
        assertFalse(hand.isBlackjack(), "Con 15 no es Blackjack")
    }

    @Test
    fun `test el as vale 11 cuando no se pasa de 21`() {
        val hand = Hand()
        hand.addCard(Card(Rank.ACE, Suit.HEARTS))
        hand.addCard(Card(Rank.NINE, Suit.SPADES))

        // 11 + 9 = 20
        assertEquals(20, hand.calculateValue(), "El As debería valer 11, total 20")
        assertTrue(hand.isSoft(), "La mano debería ser 'soft' porque el As vale 11")
    }

    @Test
    fun `test el as vale 1 cuando pasaria de 21`() {
        val hand = Hand()
        hand.addCard(Card(Rank.ACE, Suit.HEARTS))
        hand.addCard(Card(Rank.TEN, Suit.SPADES))
        hand.addCard(Card(Rank.FIVE, Suit.CLUBS))

        // 1 + 10 + 5 = 16 (si valiera 11, sería 26 y se pasaría)
        assertEquals(16, hand.calculateValue(), "El As debería valer 1 para no pasarse, total 16")
        assertFalse(hand.isSoft(), "No debería ser 'soft' porque el As está forzado a valer 1")
    }

    @Test
    fun `test deteccion de Blackjack natural`() {
        val hand = Hand()
        hand.addCard(Card(Rank.ACE, Suit.HEARTS))
        hand.addCard(Card(Rank.KING, Suit.SPADES))

        assertEquals(21, hand.calculateValue(), "El valor debe ser 21")
        assertTrue(hand.isBlackjack(), "Debería detectar Blackjack con As y figura")
    }

    @Test
    fun `test 21 con tres cartas no es Blackjack`() {
        val hand = Hand()
        hand.addCard(Card(Rank.SEVEN, Suit.HEARTS))
        hand.addCard(Card(Rank.SEVEN, Suit.SPADES))
        hand.addCard(Card(Rank.SEVEN, Suit.CLUBS))

        assertEquals(21, hand.calculateValue(), "El valor debe ser 21")
        assertFalse(hand.isBlackjack(), "Con 3 cartas sumando 21 NO es Blackjack natural")
    }

    @Test
    fun `test deteccion de mano pasada (busted)`() {
        val hand = Hand()
        hand.addCard(Card(Rank.TEN, Suit.HEARTS))
        hand.addCard(Card(Rank.EIGHT, Suit.SPADES))
        hand.addCard(Card(Rank.FOUR, Suit.CLUBS))

        // 10 + 8 + 4 = 22
        assertEquals(22, hand.calculateValue())
        assertTrue(hand.isBusted(), "Con 22 debería detectar que se ha pasado (busted)")
    }
}