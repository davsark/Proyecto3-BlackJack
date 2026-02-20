package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeckTest {

    @Test
    fun `test la baraja inicializa con 52 cartas por mazo`() {
        // Un mazo
        val singleDeck = Deck(1)
        assertEquals(52, singleDeck.remainingCards(), "Un mazo debe tener 52 cartas")

        // Dos mazos
        val doubleDeck = Deck(2)
        assertEquals(104, doubleDeck.remainingCards(), "Dos mazos deben tener 104 cartas")
    }

    @Test
    fun `test robar una carta reduce el tamaño del mazo`() {
        val deck = Deck(1)
        val initialSize = deck.remainingCards()

        deck.dealCard()

        assertEquals(initialSize - 1, deck.remainingCards(), "El mazo debe tener una carta menos tras robar")
    }

    @Test
    fun `test auto-reset cuando se acaban las cartas`() {
        val deck = Deck(1) // 52 cartas

        // Robamos las 52 cartas exactas
        repeat(52) {
            deck.dealCard()
        }
        assertEquals(0, deck.remainingCards(), "El mazo debería estar vacío")

        // Robamos una carta más. Aquí debe dispararse el reinicio automático.
        deck.dealCard()

        // El mazo vuelve a tener 52 y roba 1, por lo que deben quedar 51.
        assertEquals(51, deck.remainingCards(), "El mazo debió reiniciarse a 52 y luego restar la carta robada")
    }

    @Test
    fun `test barajar cambia el orden de las cartas`() {
        val deck1 = Deck(1)
        val deck2 = Deck(1)
        deck2.shuffle()

        var identical = true
        // Comparamos las primeras 5 cartas. Si todas son iguales en palo y valor, no se barajó.
        for (i in 0..4) {
            if (deck1.dealCard() != deck2.dealCard()) {
                identical = false
                break
            }
        }

        assertFalse(identical, "Las barajas deberían tener distinto orden tras barajar")
    }
}