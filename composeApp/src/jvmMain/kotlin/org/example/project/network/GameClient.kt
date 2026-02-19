package org.example.project.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.config.GameConfig
import org.example.project.protocol.ClientMessage
import org.example.project.protocol.ServerMessage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException

/**
 * Cliente de red que maneja la conexión con el servidor
 */
class GameClient {
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null
    private val json = Json { ignoreUnknownKeys = true }

    // Scope padre que nunca se cancela, permite reconexión sin reiniciar el objeto
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Job de la sesión activa (lectura + ping). Se cancela en disconnect()
    private var ioJob: Job? = null

    // Estado de conexión
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Mensajes recibidos del servidor
    private val _serverMessages = MutableStateFlow<ServerMessage?>(null)
    val serverMessages: StateFlow<ServerMessage?> = _serverMessages.asStateFlow()

    // Errores de conexión
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    /**
     * Conecta al servidor
     */
    suspend fun connect(
        host: String = GameConfig.DEFAULT_SERVER_HOST,
        port: Int = GameConfig.DEFAULT_SERVER_PORT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            println("🔌 Intentando conectar a $host:$port...")

            // Cancelar sesión anterior si la hubiera
            ioJob?.cancel()
            ioJob = null

            socket = Socket(host, port)
            input = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            output = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))

            _isConnected.value = true
            _connectionError.value = null

            println("✅ Conectado al servidor")

            // Iniciar lectura y keep-alive bajo el mismo job cancelable
            startIoLoops()

            true
        } catch (e: Exception) {
            val error = "Error de conexión: ${e.message}"
            println("❌ $error")
            _connectionError.value = error
            _isConnected.value = false
            false
        }
    }

    /**
     * Lanza el loop de lectura y el loop de ping como corrutinas hermanas
     * bajo un único Job que se puede cancelar en disconnect().
     */
    private fun startIoLoops() {
        ioJob = scope.launch {
            // Ping loop: envía Ping cada PING_INTERVAL_MS para evitar el soTimeout del servidor
            launch {
                while (isActive && _isConnected.value) {
                    delay(GameConfig.PING_INTERVAL_MS)
                    if (_isConnected.value) {
                        try {
                            val jsonMessage = json.encodeToString<ClientMessage>(ClientMessage.Ping)
                            withContext(Dispatchers.IO) {
                                output?.write(jsonMessage)
                                output?.newLine()
                                output?.flush()
                            }
                            println("💓 Ping enviado al servidor")
                        } catch (e: Exception) {
                            println("⚠️ Error enviando ping: ${e.message}")
                        }
                    }
                }
            }

            // Loop principal de lectura de mensajes
            try {
                while (_isConnected.value) {
                    val line = input?.readLine()

                    if (line == null) {
                        println("🔌 Servidor cerró la conexión")
                        disconnect()
                        _connectionError.value = "El servidor cerró la conexión"
                        break
                    }

                    try {
                        val message = json.decodeFromString<ServerMessage>(line)
                        println("📨 Mensaje recibido: ${message::class.simpleName}")
                        _serverMessages.value = message
                    } catch (e: Exception) {
                        println("⚠️ Error al parsear mensaje: ${e.message}")
                        println("   Mensaje raw: $line")
                    }
                }
            } catch (e: SocketException) {
                if (_isConnected.value) {
                    println("❌ Error de socket: ${e.message}")
                    _connectionError.value = "Conexión perdida: ${e.message}"
                    disconnect()
                }
            } catch (e: Exception) {
                println("❌ Error leyendo mensajes: ${e.message}")
                _connectionError.value = "Error de lectura: ${e.message}"
                disconnect()
            }
        }
    }

    /**
     * Envía un mensaje al servidor
     */
    suspend fun sendMessage(message: ClientMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!_isConnected.value || output == null) {
                println("⚠️ No hay conexión para enviar mensaje")
                return@withContext false
            }

            val jsonMessage = json.encodeToString(message)
            output?.write(jsonMessage)
            output?.newLine()
            output?.flush()

            println("📤 Mensaje enviado: ${message::class.simpleName}")
            true
        } catch (e: Exception) {
            println("❌ Error al enviar mensaje: ${e.message}")
            _connectionError.value = "Error al enviar: ${e.message}"
            false
        }
    }

    /**
     * Desconecta del servidor. Cancela solo el ioJob de la sesión,
     * dejando el scope padre activo para futuras reconexiones.
     */
    fun disconnect() {
        try {
            _isConnected.value = false
            socket?.close()
            ioJob?.cancel()
            ioJob = null
            println("👋 Desconectado del servidor")
        } catch (e: Exception) {
            println("⚠️ Error al desconectar: ${e.message}")
        }
    }

    /**
     * Limpia el último mensaje recibido
     */
    fun clearLastMessage() {
        _serverMessages.value = null
    }

    /**
     * Limpia el error de conexión
     */
    fun clearError() {
        _connectionError.value = null
    }
}
