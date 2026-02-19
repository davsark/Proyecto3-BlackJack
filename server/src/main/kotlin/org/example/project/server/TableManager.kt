package org.example.project.server

import org.example.project.protocol.GameSettings
import org.example.project.protocol.ServerMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestor de mesas de Blackjack
 * 
 * Administra la creación y asignación de mesas para el modo PvP
 */
class TableManager(
    private val gameSettings: GameSettings,
    private val maxPlayersPerTable: Int = 4
) {
    private val tables = ConcurrentHashMap<String, Table>()
    private val playerTableMap = ConcurrentHashMap<String, String>() // playerId -> tableId
    private val tableIdCounter = AtomicInteger(1)

    init {
        println("🎰 TableManager inicializado")
    }

    /**
     * Busca una mesa disponible o crea una nueva
     */
    suspend fun findOrCreateTable(
        playerId: String,
        playerName: String,
        chips: Int,
        callback: suspend (ServerMessage) -> Unit
    ): Table? {
        // Buscar mesa con espacio
        var table = tables.values.find { !it.isFull() && it.getPlayerCount() > 0 }
        
        // Si no hay mesa disponible, crear una nueva
        if (table == null) {
            val tableId = "table-${tableIdCounter.getAndIncrement()}"
            table = Table(tableId, gameSettings, maxPlayersPerTable)
            tables[tableId] = table
            println("🆕 Nueva mesa creada: $tableId")
        }
        
        // Unir al jugador a la mesa
        val joined = table.addPlayer(playerId, playerName, chips, callback)
        
        if (joined) {
            playerTableMap[playerId] = table.tableId
            println("✅ $playerName unido a mesa ${table.tableId}")
            return table
        }
        
        return null
    }

    /**
     * Obtiene la mesa de un jugador
     */
    fun getPlayerTable(playerId: String): Table? {
        val tableId = playerTableMap[playerId] ?: return null
        return tables[tableId]
    }

    /**
     * Elimina un jugador de su mesa
     */
    suspend fun removePlayer(playerId: String) {
        val tableId = playerTableMap.remove(playerId) ?: return
        val table = tables[tableId] ?: return
        
        table.removePlayer(playerId)
        
        // Si la mesa está vacía, eliminarla
        if (table.isEmpty()) {
            tables.remove(tableId)
            println("🗑️ Mesa $tableId eliminada (vacía)")
        }
    }

    /**
     * Obtiene estadísticas del TableManager
     */
    fun getStats(): String {
        val totalPlayers = playerTableMap.size
        val totalTables = tables.size
        return "Mesas: $totalTables, Jugadores: $totalPlayers"
    }
}
