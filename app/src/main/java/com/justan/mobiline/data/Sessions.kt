package com.justan.mobiline.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One per-wheel measurement captured in a session. */
data class SavedMeasurement(
    val wheel: String,
    val value: Double
)

/** A named, timestamped set of measurements from one measurement session. */
data class SavedSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val mode: String,                 // "Camber" or "Toe"
    val measurements: List<SavedMeasurement>
)

/** Default session name: current date + time, e.g. "2026-09-02 17:45". */
fun defaultSessionName(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun SavedSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("createdAt", createdAt)
    put("mode", mode)
    put(
        "measurements", JSONArray().apply {
            measurements.forEach { put(JSONObject().apply { put("wheel", it.wheel); put("value", it.value) }) }
        }
    )
}

private fun sessionFromJson(o: JSONObject): SavedSession {
    val ms = o.getJSONArray("measurements")
    val list = (0 until ms.length()).map {
        val m = ms.getJSONObject(it)
        SavedMeasurement(m.getString("wheel"), m.getDouble("value"))
    }
    return SavedSession(
        id = o.getString("id"),
        name = o.getString("name"),
        createdAt = o.getLong("createdAt"),
        mode = o.getString("mode"),
        measurements = list
    )
}

/**
 * Simple local persistence for measurement sessions. Sessions are stored as
 * a JSON array in a single file under the app's internal storage -- no
 * permissions, no external access. Save/load run on Dispatchers.IO.
 */
class SessionStore(context: Context) {

    private val file = File(context.filesDir, "sessions.json")

    /** Append [session] to the persisted list. */
    suspend fun save(session: SavedSession) = withContext(Dispatchers.IO) {
        val all = loadBlocking() + session
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    /** All persisted sessions, oldest first. */
    suspend fun load(): List<SavedSession> = withContext(Dispatchers.IO) { loadBlocking() }

    private fun loadBlocking(): List<SavedSession> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { sessionFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}