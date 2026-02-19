package org.example.project.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.config.GameConfig
import org.example.project.protocol.GameResultType
import org.example.project.protocol.Record
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Gestor de records del juego
 * Guarda y lee records en formato JSON con estadísticas extendidas
 */
class RecordsManager(private val recordsFilePath: String = "records.json") {
    private val recordsFile = File(recordsFilePath)
    private val records = mutableMapOf<String, PlayerStats>()
    private val lock = ReentrantReadWriteLock()
    private val json = Json { prettyPrint = true }

    data class PlayerStats(
        val playerName: String,
        var wins: Int = 0,
        var losses: Int = 0,
        var blackjacks: Int = 0,
        var surrenders: Int = 0,
        var pushes: Int = 0,
        var gamesPlayed: Int = 0,
        var maxChips: Int = GameConfig.INITIAL_CHIPS,
        var currentStreak: Int = 0,
        var bestStreak: Int = 0,
        var totalGain: Int = 0,
        var bestHand: String = "",
        var lastPlayed: Long = System.currentTimeMillis()
    )

    init {
        loadRecords()
    }

    /**
     * Carga los records desde el archivo JSON
     */
    private fun loadRecords() = lock.write {
        try {
            if (recordsFile.exists()) {
                val jsonContent = recordsFile.readText()
                val loadedRecords = json.decodeFromString<List<Record>>(jsonContent)
                loadedRecords.forEach { record ->
                    records[record.playerName] = PlayerStats(
                        playerName = record.playerName,
                        wins = record.wins,
                        losses = record.losses,
                        blackjacks = record.blackjacks,
                        maxChips = record.maxChips,
                        currentStreak = record.currentStreak,
                        bestStreak = record.bestStreak,
                        totalGain = record.totalGain,
                        bestHand = record.bestHand,
                        gamesPlayed = record.gamesPlayed,
                        lastPlayed = record.timestamp
                    )
                }
                println("✅ Records cargados: ${records.size} jugadores")
            } else {
                println("ℹ️ No hay archivo de records previo, se creará uno nuevo")
            }
        } catch (e: Exception) {
            println("⚠️ Error al cargar records: ${e.message}")
            println("   Se iniciará con records vacíos")
        }
    }

    /**
     * Guarda los records en el archivo JSON
     */
    private fun saveRecords() {
        try {
            val recordsList = records.values.map { stats ->
                Record(
                    playerName = stats.playerName,
                    wins = stats.wins,
                    losses = stats.losses,
                    blackjacks = stats.blackjacks,
                    timestamp = stats.lastPlayed,
                    maxChips = stats.maxChips,
                    currentStreak = stats.currentStreak,
                    bestStreak = stats.bestStreak,
                    totalGain = stats.totalGain,
                    bestHand = stats.bestHand,
                    gamesPlayed = stats.gamesPlayed
                )
            }.sortedByDescending { it.wins }
                .take(GameConfig.MAX_RECORDS)

            val jsonContent = json.encodeToString(recordsList)
            recordsFile.writeText(jsonContent)
            println("💾 Records guardados: ${recordsList.size} jugadores")
        } catch (e: Exception) {
            println("❌ Error al guardar records: ${e.message}")
        }
    }

    /**
     * Registra el resultado de una partida con información de apuestas
     */
    fun recordGameResult(
        playerName: String,
        result: GameResultType,
        bet: Int,
        payout: Int,
        finalChips: Int
    ) = lock.write {
        val stats = records.getOrPut(playerName) {
            PlayerStats(playerName = playerName)
        }

        stats.gamesPlayed++
        stats.totalGain += payout
        
        when (result) {
            GameResultType.WIN -> {
                stats.wins++
                stats.currentStreak = if (stats.currentStreak >= 0) stats.currentStreak + 1 else 1
            }
            GameResultType.LOSE -> {
                stats.losses++
                stats.currentStreak = if (stats.currentStreak <= 0) stats.currentStreak - 1 else -1
            }
            GameResultType.PUSH -> {
                stats.pushes++
                // Empate no afecta la racha
            }
            GameResultType.BLACKJACK -> {
                stats.wins++
                stats.blackjacks++
                stats.currentStreak = if (stats.currentStreak >= 0) stats.currentStreak + 1 else 1
            }
            GameResultType.SURRENDER -> {
                stats.surrenders++
                stats.currentStreak = if (stats.currentStreak <= 0) stats.currentStreak - 1 else -1
            }
        }

        // Actualizar mejor racha
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak
        }

        // Actualizar fichas máximas
        if (finalChips > stats.maxChips) {
            stats.maxChips = finalChips
        }

        stats.lastPlayed = System.currentTimeMillis()
        saveRecords()

        println("📊 Record actualizado: $playerName (${stats.wins}W-${stats.losses}L, Racha: ${stats.currentStreak}, Mejor: ${stats.bestStreak}, Max fichas: ${stats.maxChips})")
    }

    /**
     * Obtiene el top de records
     */
    fun getTopRecords(limit: Int = GameConfig.TOP_RECORDS_DISPLAY): List<Record> = lock.read {
        records.values
            .map { stats ->
                Record(
                    playerName = stats.playerName,
                    wins = stats.wins,
                    losses = stats.losses,
                    blackjacks = stats.blackjacks,
                    timestamp = stats.lastPlayed,
                    maxChips = stats.maxChips,
                    currentStreak = stats.currentStreak,
                    bestStreak = stats.bestStreak,
                    totalGain = stats.totalGain,
                    bestHand = stats.bestHand,
                    gamesPlayed = stats.gamesPlayed
                )
            }
            .sortedWith(compareByDescending<Record> { it.wins }.thenBy { it.losses })
            .take(limit)
    }

    /**
     * Obtiene las estadísticas de un jugador específico
     */
    fun getPlayerStats(playerName: String): Record? = lock.read {
        records[playerName]?.let { stats ->
            Record(
                playerName = stats.playerName,
                wins = stats.wins,
                losses = stats.losses,
                blackjacks = stats.blackjacks,
                timestamp = stats.lastPlayed,
                maxChips = stats.maxChips,
                currentStreak = stats.currentStreak,
                bestStreak = stats.bestStreak,
                totalGain = stats.totalGain,
                bestHand = stats.bestHand,
                gamesPlayed = stats.gamesPlayed
            )
        }
    }
}
