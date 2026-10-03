package org.shilpo.laboon.rip

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RipWebSocketClient(
    private val sessionStore: SessionStore,
    private val clientScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(RipVisualizerState())
    val state: StateFlow<RipVisualizerState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        connect()
    }

    fun stop() {
        isRunning.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectSocket()
        _state.update { it.copy(wsStatus = RipWsStatus.DISCONNECTED) }
    }

    private fun disconnectSocket() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    private fun connect() {
        val session = sessionStore.getSession()
        if (session == null) {
            _state.update {
                it.copy(
                    wsStatus = RipWsStatus.ERROR,
                    errorMessage = "Not signed in to server",
                )
            }
            return
        }

        val baseServerUrl = session.serverUrl.trim().removeSuffix("/")
        val wsUrl = buildWsUrl(baseServerUrl)
        val token = session.token.trim().removePrefix("Bearer ").trim()

        _state.update {
            it.copy(
                wsStatus = RipWsStatus.CONNECTING,
                errorMessage = null,
            )
        }

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        disconnectSocket()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isRunning.get()) return
                _state.update {
                    it.copy(
                        wsStatus = RipWsStatus.CONNECTED,
                        errorMessage = null,
                    )
                }

                val deviceName =
                    "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                val helloMsg = JSONObject().apply {
                    put("type", "hello")
                    put(
                        "payload",
                        JSONObject().apply {
                            put("device_id", "laboon_android_${Build.ID.take(6)}")
                            put("device_name", deviceName)
                            put("platform", "android")
                        }
                    )
                }
                webSocket.send(helloMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isRunning.get()) return
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isRunning.get()) return
                val errorMsg = t.message ?: "WebSocket connection failed (${response?.code ?: 0})"
                _state.update {
                    it.copy(
                        wsStatus = RipWsStatus.ERROR,
                        errorMessage = errorMsg,
                    )
                }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isRunning.get()) return
                _state.update { it.copy(wsStatus = RipWsStatus.DISCONNECTED) }
                scheduleReconnect()
            }
        })
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)
            val type = root.optString("type")
            val payload = root.objOrNull("payload") ?: return

            when (type) {
                "rip_tasks_snapshot" -> {
                    val tasksArr = payload.arrOrNull("tasks") ?: JSONArray()
                    val prevMap = _state.value.activeTasks.associateBy { it.taskId }
                    val newTasks = mutableListOf<RipTaskSnapshot>()
                    for (i in 0 until tasksArr.length()) {
                        val taskObj = tasksArr.objAtOrNull(i) ?: continue
                        val taskId = taskObj.optString("task_id")
                        val prev = prevMap[taskId]
                        newTasks.add(RipTaskSnapshot.fromJson(taskObj, prev))
                    }
                    _state.update { it.copy(activeTasks = newTasks) }
                }

                "rip_task_updated" -> {
                    val taskObj = payload.objOrNull("task") ?: return
                    val taskId = taskObj.optString("task_id")
                    if (taskId.isEmpty()) return

                    val previous = _state.value.activeTasks.find { it.taskId == taskId }
                    val updated = RipTaskSnapshot.fromJson(taskObj, previous)
                    _state.update { current ->
                        val existingIndex = current.activeTasks.indexOfFirst { it.taskId == taskId }
                        val newActive = if (existingIndex >= 0) {
                            current.activeTasks.toMutableList()
                                .apply { set(existingIndex, updated) }
                        } else {
                            listOf(updated) + current.activeTasks
                        }
                        current.copy(activeTasks = newActive)
                    }
                }

                "rip_task_dismissed" -> {
                    val taskId = payload.optString("task_id")
                    if (taskId.isEmpty()) return
                    _state.update { current ->
                        current.copy(
                            activeTasks = current.activeTasks.filter { it.taskId != taskId },
                        )
                    }
                }

                "error" -> {
                    val message = payload.optString("message", "Server error received")
                    _state.update { it.copy(errorMessage = message) }
                }
            }
        } catch (e: Exception) {
            Log.w("RipWS", "Failed to parse message: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning.get()) return
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(3_000)
            if (isRunning.get()) {
                connect()
            }
        }
    }

    fun cancelTask(taskId: String) {
        val ws = webSocket ?: return
        try {
            val req = JSONObject().apply {
                put("type", "cancel_rip_task")
                put(
                    "payload",
                    JSONObject().apply {
                        put("request_id", "req_${System.currentTimeMillis()}")
                        put("task_id", taskId)
                    }
                )
            }
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w("RipWS", "Failed to cancel task: ${e.message}")
        }
    }

    private fun buildWsUrl(baseServerUrl: String): String {
        val clean = baseServerUrl.trim().removeSuffix("/")
        val wsScheme = if (clean.startsWith("https://", ignoreCase = true)) {
            "wss://"
        } else {
            "ws://"
        }
        val hostPart = clean
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
        return "$wsScheme$hostPart/api/v1/ws/sync"
    }
}
