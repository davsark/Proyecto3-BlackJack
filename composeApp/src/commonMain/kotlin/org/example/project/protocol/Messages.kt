package org.example.project.protocol

import kotlinx.serialization.Serializable

/**
 * Mensajes del Cliente → Servidor
 */
@Serializable
sealed class ClientMessage {
    /**
     * El jugador se une al servidor
     */
    @Serializable
    data class JoinGame(
        val playerName: String,
        val gameMode: GameMode,
        val buyIn: Int = 1000,
        val settings: GameSettings? = null
    ) : ClientMessage()

    /**
     * El jugador realiza una apuesta (soporta múltiples manos)
     * @param amount Apuesta por mano
     * @param numberOfHands Número de manos a jugar (1-4)
     */
    @Serializable
    data class PlaceBet(
        val amount: Int,
        val numberOfHands: Int = 1
    ) : ClientMessage()

    /**
     * El jugador pide una carta (HIT)
     */
    @Serializable
    data object RequestCard : ClientMessage()

    /**
     * El jugador se planta (STAND)
     */
    @Serializable
    data object Stand : ClientMessage()

    /**
     * El jugador dobla su apuesta (DOUBLE)
     */
    @Serializable
    data object Double : ClientMessage()

    /**
     * El jugador divide su mano (SPLIT)
     */
    @Serializable
    data object Split : ClientMessage()

    /**
     * El jugador se rinde (SURRENDER)
     */
    @Serializable
    data object Surrender : ClientMessage()

    /**
     * El jugador solicita empezar una nueva partida
     */
    @Serializable
    data object NewGame : ClientMessage()

    /**
     * El jugador solicita ver los records
     */
    @Serializable
    data object RequestRecords : ClientMessage()

    /**
     * El jugador solicita el historial de manos
     */
    @Serializable
    data object RequestHistory : ClientMessage()

    /**
     * El jugador selecciona una mano específica para jugar (multi-mano)
     * @param handIndex Índice de la mano (0, 1, 2)
     */
    @Serializable
    data class SelectHand(val handIndex: Int) : ClientMessage()

    /**
     * Ping para mantener la conexión viva
     */
    @Serializable
    data object Ping : ClientMessage()
}

/**
 * Mensajes del Servidor → Cliente
 */
@Serializable
sealed class ServerMessage {
    /**
     * Confirmación de unión al juego
     */
    @Serializable
    data class JoinConfirmation(
        val playerId: String,
        val message: String,
        val initialChips: Int = 1000
    ) : ServerMessage()

    /**
     * Estado de la mesa (enviado al conectar y cuando cambia)
     */
    @Serializable
    data class TableState(
        val players: List<PlayerInfo>,
        val minBet: Int,
        val maxBet: Int,
        val currentPlayerChips: Int
    ) : ServerMessage()

    /**
     * Solicitud de apuesta
     */
    @Serializable
    data class RequestBet(
        val minBet: Int,
        val maxBet: Int,
        val currentChips: Int
    ) : ServerMessage()

    /**
     * Estado completo del juego (soporta múltiples manos)
     */
    @Serializable
    data class GameState(
        val playerHand: List<Card>,
        val dealerHand: List<Card>,
        val playerScore: Int,
        val dealerScore: Int,
        val gameState: GamePhase,
        val canRequestCard: Boolean,
        val canStand: Boolean,
        val canDouble: Boolean = false,
        val canSplit: Boolean = false,
        val canSurrender: Boolean = false,
        val currentBet: Int = 0,
        val playerChips: Int = 1000,
        val splitHand: List<Card>? = null,
        val splitScore: Int? = null,
        val activeSplitHand: Int = 0,
        val bustProbability: Double = 0.0,
        val otherPlayers: List<PlayerInfo> = emptyList(),
        // Soporte para múltiples manos
        val multipleHands: List<MultiHandState> = emptyList(),
        val activeHandIndex: Int = 0,
        val numberOfHands: Int = 1,
        val totalBet: Int = 0
    ) : ServerMessage()

    /**
     * Resultado final de la partida (con soporte para múltiples manos)
     */
    @Serializable
    data class GameResult(
        val result: GameResultType,
        val playerFinalScore: Int,
        val dealerFinalScore: Int,
        val message: String,
        val dealerFinalHand: List<Card>,
        val payout: Int = 0,
        val newChipsTotal: Int = 0,
        val splitResult: GameResultType? = null,
        val splitPayout: Int? = null,
        // Resultados de múltiples manos
        val handResults: List<SingleHandResult> = emptyList()
    ) : ServerMessage()

    /**
     * Estado de la mesa PvP (enviado a todos los jugadores)
     */
    @Serializable
    data class PvPTableState(
        val tableId: String,
        val phase: String,
        val roundNumber: Int,
        val players: List<PvPPlayerInfo>,
        val dealerCards: List<Card>,
        val dealerScore: Int,
        val currentTurnPlayerId: String?,
        val currentPlayerId: String, // ID del jugador que recibe este mensaje
        val minBet: Int,
        val maxBet: Int
    ) : ServerMessage()

    /**
     * Lista de records
     */
    @Serializable
    data class RecordsList(
        val records: List<Record>
    ) : ServerMessage()

    /**
     * Historial de manos jugadas
     */
    @Serializable
    data class HandHistoryList(
        val history: List<HandHistory>
    ) : ServerMessage()

    /**
     * Error del servidor
     */
    @Serializable
    data class Error(
        val errorMessage: String
    ) : ServerMessage()

    /**
     * Respuesta al ping
     */
    @Serializable
    data object Pong : ServerMessage()
}

/**
 * Modos de juego
 */
@Serializable
enum class GameMode {
    PVE,
    PVP
}

/**
 * Fases del juego
 */
@Serializable
enum class GamePhase {
    WAITING,
    BETTING,
    PLAYER_TURN,
    DEALER_TURN,
    GAME_OVER
}

/**
 * Tipos de resultado
 */
@Serializable
enum class GameResultType {
    WIN,
    LOSE,
    PUSH,
    BLACKJACK,
    SURRENDER
}

/**
 * Representación de una carta
 */
@Serializable
data class Card(
    val rank: Rank,
    val suit: Suit,
    val hidden: Boolean = false
) {
    override fun toString(): String {
        return if (hidden) "[OCULTA]" else "${rank.symbol}${suit.symbol}"
    }
    
    /**
     * Nombre del archivo de imagen para esta carta
     * Formato: card_{suit}_{rank} (ej: card_hearts_A, card_spades_02)
     */
    fun getImageName(): String {
        if (hidden) return "card_back"
        val rankName = when (rank) {
            Rank.ACE -> "A"
            Rank.TWO -> "02"
            Rank.THREE -> "03"
            Rank.FOUR -> "04"
            Rank.FIVE -> "05"
            Rank.SIX -> "06"
            Rank.SEVEN -> "07"
            Rank.EIGHT -> "08"
            Rank.NINE -> "09"
            Rank.TEN -> "10"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
        }
        val suitName = when (suit) {
            Suit.HEARTS -> "hearts"
            Suit.DIAMONDS -> "diamonds"
            Suit.CLUBS -> "clubs"
            Suit.SPADES -> "spades"
        }
        return "card_${suitName}_${rankName}"
    }
}

/**
 * Palos de la baraja
 */
@Serializable
enum class Suit(val symbol: String, val displayName: String) {
    HEARTS("♥", "Corazones"),
    DIAMONDS("♦", "Diamantes"),
    CLUBS("♣", "Tréboles"),
    SPADES("♠", "Picas")
}

/**
 * Rangos de las cartas
 */
@Serializable
enum class Rank(val symbol: String, val values: List<Int>) {
    ACE("A", listOf(1, 11)),
    TWO("2", listOf(2)),
    THREE("3", listOf(3)),
    FOUR("4", listOf(4)),
    FIVE("5", listOf(5)),
    SIX("6", listOf(6)),
    SEVEN("7", listOf(7)),
    EIGHT("8", listOf(8)),
    NINE("9", listOf(9)),
    TEN("10", listOf(10)),
    JACK("J", listOf(10)),
    QUEEN("Q", listOf(10)),
    KING("K", listOf(10));

    val value: Int get() = values.first()
}

/**
 * Record de un jugador
 */
@Serializable
data class Record(
    val playerName: String,
    val wins: Int,
    val losses: Int,
    val blackjacks: Int,
    val timestamp: Long,
    val maxChips: Int = 1000,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalGain: Int = 0,
    val bestHand: String = "",
    val gamesPlayed: Int = 0
) {
    val winRate: Double
        get() = if (wins + losses > 0) wins.toDouble() / (wins + losses) else 0.0
}

/**
 * Información de otros jugadores (para modo PVP)
 */
@Serializable
data class PlayerInfo(
    val playerName: String,
    val handSize: Int,
    val score: Int,
    val hasStood: Boolean,
    val chips: Int = 1000,
    val currentBet: Int = 0
)

/**
 * Configuración del juego (enviada al cliente)
 */
@Serializable
data class GameSettings(
    val numberOfDecks: Int = 1,
    val initialChips: Int = 1000,
    val minBet: Int = 10,
    val maxBet: Int = 500,
    val blackjackPayout: Double = 1.5,
    val dealerHitsOnSoft17: Boolean = false,
    val allowDoubleAfterSplit: Boolean = true,
    val allowSurrender: Boolean = true,
    val maxSplits: Int = 3
)

/**
 * Historial de una mano jugada
 */
@Serializable
data class HandHistory(
    val playerHand: List<Card>,
    val dealerHand: List<Card>,
    val result: GameResultType,
    val bet: Int,
    val payout: Int,
    val timestamp: Long,
    val playerScore: Int = 0,
    val dealerScore: Int = 0
)

/**
 * Estado de una mano individual en modo multi-mano
 */
@Serializable
data class MultiHandState(
    val handIndex: Int,
    val cards: List<Card>,
    val score: Int,
    val bet: Int,
    val status: HandStatus,
    val canHit: Boolean,
    val canStand: Boolean,
    val canDouble: Boolean,
    val canSplit: Boolean,
    val result: GameResultType? = null,
    val payout: Int = 0
)

/**
 * Estado de una mano en modo multi-mano
 */
@Serializable
enum class HandStatus {
    WAITING,      // Esperando turno
    PLAYING,      // Turno activo
    STANDING,     // Se plantó
    BUSTED,       // Se pasó
    BLACKJACK,    // Blackjack natural
    COMPLETED     // Finalizada
}

/**
 * Resultado de una mano individual (para múltiples manos)
 */
@Serializable
data class SingleHandResult(
    val handIndex: Int,
    val cards: List<Card>,
    val score: Int,
    val bet: Int,
    val result: GameResultType,
    val payout: Int
)

/**
 * Información de un jugador en mesa PvP
 */
@Serializable
data class PvPPlayerInfo(
    val playerId: String,
    val name: String,
    val chips: Int,
    val currentBet: Int,
    val cards: List<Card>,
    val score: Int,
    val status: String,
    val isCurrentTurn: Boolean,
    val isBusted: Boolean,
    val isBlackjack: Boolean,
    val isStanding: Boolean
)
