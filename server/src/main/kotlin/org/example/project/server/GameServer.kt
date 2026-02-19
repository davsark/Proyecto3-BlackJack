package org.example.project.server

import kotlinx.coroutines.*
import org.example.project.config.GameConfig
import org.example.project.protocol.GameSettings
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servidor principal del juego de Blackjack
 * Acepta conexiones de clientes y las maneja con corrutinas
 */
class GameServer {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recordsManager: RecordsManager
    private var isRunning = false
    
    // Configuración cargada desde archivo
    private val config: Map<String, String>
    private val host: String
    private val port: Int
    private val maxClients: Int
    private val currentClients = AtomicInteger(0)
    
    // Configuración del juego
    val gameSettings: GameSettings
    
    // Gestor de mesas PvP
    val tableManager: TableManager

    init {
        // Cargar configuración
        config = loadConfiguration()
        host = config["server.host"] ?: GameConfig.DEFAULT_SERVER_HOST
        port = config["server.port"]?.toIntOrNull() ?: GameConfig.DEFAULT_SERVER_PORT
        maxClients = config["max.clients"]?.toIntOrNull() ?: GameConfig.MAX_CLIENTS
        
        val recordsFile = config["server.recordsFile"] ?: "records.json"
        recordsManager = RecordsManager(recordsFile)
        
        // Configuración del juego
        gameSettings = GameSettings(
            numberOfDecks = config["game.numberOfDecks"]?.toIntOrNull() ?: GameConfig.DEFAULT_NUMBER_OF_DECKS,
            initialChips = config["game.initialChips"]?.toIntOrNull() ?: GameConfig.INITIAL_CHIPS,
            minBet = config["game.minBet"]?.toIntOrNull() ?: GameConfig.MIN_BET,
            maxBet = config["game.maxBet"]?.toIntOrNull() ?: GameConfig.MAX_BET,
            blackjackPayout = config["game.blackjackPayout"]?.toDoubleOrNull() ?: GameConfig.BLACKJACK_PAYOUT,
            dealerHitsOnSoft17 = config["game.dealerHitsOnSoft17"]?.toBoolean() ?: GameConfig.DEALER_HITS_ON_SOFT_17,
            allowDoubleAfterSplit = GameConfig.ALLOW_DOUBLE_AFTER_SPLIT,
            allowSurrender = GameConfig.ALLOW_SURRENDER,
            maxSplits = GameConfig.MAX_SPLITS
        )

        println("📋 Configuración del servidor:")
        println("   Host: $host")
        println("   Puerto: $port")
        println("   Máximo de clientes: $maxClients")
        println("   Fichas iniciales: ${gameSettings.initialChips}")
        println("   Apuesta mínima: ${gameSettings.minBet}")
        println("   Apuesta máxima: ${gameSettings.maxBet}")
        println("   Número de mazos: ${gameSettings.numberOfDecks}")
        println("   Pago por Blackjack: ${gameSettings.blackjackPayout}x")
        
        // Inicializar gestor de mesas PvP
        tableManager = TableManager(gameSettings, maxPlayersPerTable = 4)
    }

    /**
     * Carga la configuración desde el archivo properties
     */
    private fun loadConfiguration(): Map<String, String> {
        val configMap = mutableMapOf<String, String>()

        try {
            val configFile = File("server/src/main/resources/server-config.properties")
            if (configFile.exists()) {
                val properties = Properties()
                configFile.inputStream().use { properties.load(it) }
                properties.forEach { key, value ->
                    configMap[key.toString()] = value.toString()
                }
                println("✅ Configuración cargada desde: ${configFile.absolutePath}")
            } else {
                println("⚠️ No se encontró archivo de configuración, usando valores por defecto")
            }
        } catch (e: Exception) {
            println("⚠️ Error al cargar configuración: ${e.message}")
            println("   Usando valores por defecto")
        }

        return configMap
    }

    /**
     * Inicia el servidor
     */
    fun start() {
        try {
            val bindAddress = InetAddress.getByName(host)
            serverSocket = ServerSocket(port, 50, bindAddress)
            isRunning = true

            println()
            println("=" .repeat(60))
            println("🎰 SERVIDOR DE BLACKJACK INICIADO")
            println("=" .repeat(60))
            println("📡 Host: $host")
            println("📡 Puerto: $port")
            println("👥 Máximo de clientes: $maxClients")
            println("🎮 Esperando conexiones de clientes...")
            println("🛑 Presiona Ctrl+C para detener el servidor")
            println("=" .repeat(60))
            println()

            // Loop principal de aceptación de conexiones
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break

                    // Verificar límite de clientes
                    if (currentClients.get() >= maxClients) {
                        println("⚠️ Límite de clientes alcanzado ($maxClients). Rechazando conexión.")
                        clientSocket.close()
                        continue
                    }

                    currentClients.incrementAndGet()
                    println("🔔 Nueva conexión desde: ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
                    println("   Clientes conectados: ${currentClients.get()}/$maxClients")

                    // Lanzar una corrutina para manejar este cliente
                    scope.launch {
                        val handler = ClientHandler(clientSocket, recordsManager, gameSettings, tableManager)
                        try {
                            handler.handle()
                        } catch (e: Exception) {
                            println("❌ Error manejando cliente: ${e.message}")
                        } finally {
                            currentClients.decrementAndGet()
                            println("👋 Cliente desconectado. Clientes restantes: ${currentClients.get()}/$maxClients")
                        }
                    }
                } catch (e: SocketException) {
                    if (isRunning) {
                        println("⚠️ Error en socket: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Error fatal en servidor: ${e.message}")
            e.printStackTrace()
        } finally {
            stop()
        }
    }

    /**
     * Detiene el servidor
     */
    fun stop() {
        println()
        println("🛑 Deteniendo servidor...")
        isRunning = false

        try {
            serverSocket?.close()
            scope.cancel()
            println("✅ Servidor detenido correctamente")
        } catch (e: Exception) {
            println("⚠️ Error al detener servidor: ${e.message}")
        }
    }
}

/**
 * Punto de entrada del servidor
 */
fun main() {
    val server = GameServer()

    // Manejar cierre graceful
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })

    server.start()
}
