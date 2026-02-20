package org.example.project.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStatsTest {

    @Test
    fun `test calculo del win rate (porcentaje de victorias)`() {
        // Simular 10 manos jugadas, 4 ganadas
        val stats = SessionStats(
            handsPlayed = 10,
            handsWon = 4,
            handsLost = 6
        )

        // 4/10 = 0.4
        assertEquals(0.4, stats.winRate, "El winRate debería ser exactamente 0.4 (40%)")
    }

    @Test
    fun `test el win rate es 0 cuando no se han jugado manos`() {
        // Evitar división por cero
        val stats = SessionStats(handsPlayed = 0, handsWon = 0)

        assertEquals(0.0, stats.winRate, "El winRate debe ser 0.0 si no hay manos jugadas")
    }

    @Test
    fun `test el win rate es 1 cuando se ganan todas las manos`() {
        val stats = SessionStats(handsPlayed = 5, handsWon = 5)

        assertEquals(1.0, stats.winRate, "El winRate debe ser 1.0 (100%) si se gana todo")
    }
}