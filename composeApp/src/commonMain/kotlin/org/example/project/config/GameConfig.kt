package org.example.project.config

/**
 * Configuración del juego
 */
object GameConfig {
    // Servidor
    const val DEFAULT_SERVER_PORT = 9999
    const val DEFAULT_SERVER_HOST = "localhost"
    const val MAX_CLIENTS = 10
    const val MAX_PLAYERS_PER_TABLE = 5
    const val CONNECTION_TIMEOUT_MS = 60_000L
    const val PING_INTERVAL_MS = 30_000L

    // Baraja
    const val DECK_RESET_THRESHOLD = 15
    const val DEFAULT_NUMBER_OF_DECKS = 1

    // Fichas y apuestas
    const val INITIAL_CHIPS = 1000
    const val MIN_BET = 10
    const val MAX_BET = 500

    // Pagos
    const val BLACKJACK_PAYOUT = 1.5
    const val REGULAR_WIN_PAYOUT = 1.0
    const val SURRENDER_RETURN = 0.5

    // Reglas del dealer
    const val DEALER_HITS_ON_SOFT_17 = false
    const val DEALER_STAND_VALUE = 17

    // Splits
    const val MAX_SPLITS = 3
    const val ALLOW_DOUBLE_AFTER_SPLIT = true
    const val ALLOW_SURRENDER = true

    // Records
    const val MAX_RECORDS = 100
    const val TOP_RECORDS_DISPLAY = 10
    const val HAND_HISTORY_SIZE = 10
}
