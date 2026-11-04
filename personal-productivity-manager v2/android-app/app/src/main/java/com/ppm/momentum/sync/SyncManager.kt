package com.ppm.momentum.sync

import com.ppm.momentum.data.PrefsRepository
import com.ppm.momentum.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

sealed class SyncResult {
    data class Success(val pushed: Int, val pulled: Int) : SyncResult()
    object NotSignedIn : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Two-way sync with the backend.
 *  - push cursor uses the DEVICE clock (rows are stamped with it), pull cursor uses the SERVER clock,
 *    so a wrong phone clock cannot make local edits fall behind the cursor and never upload.
 *  - conflicts: last-write-wins on updatedAt; deletions are tombstones.
 *  - task activity history stays on the device (not synced).
 */
class SyncManager(private val repo: TaskRepository, private val prefs: PrefsRepository) {
    private val lock = Mutex()

    suspend fun signInWithGoogleToken(serverUrl: String, idToken: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            prefs.setServerUrl(serverUrl)
            val url = prefs.prefs.first().serverUrl
            val res = Http.request("POST", "$url/auth/google", JSONObject().put("idToken", idToken))
            prefs.setAuth(res.getString("token"), res.getJSONObject("user").optString("email", ""))
            sync()
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Sign-in failed")
        }
    }

    suspend fun signOut() = prefs.clearAuth()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val p = prefs.prefs.first()
                if (p.authToken.isBlank() || p.serverUrl.isBlank()) return@withLock SyncResult.NotSignedIn
                val started = System.currentTimeMillis()

                // profile / WhatsApp preferences (small; sent every time so the server stays current)
                Http.request(
                    "PUT", "${p.serverUrl}/me/settings",
                    JSONObject().put("name", p.name).put("timezone", ZoneId.systemDefault().id)
                        .put("whatsappNumber", p.whatsappNumber.removePrefix("+")).put("whatsappEnabled", p.whatsappEnabled && p.whatsappNumber.isNotBlank())
                        .put("morningTime", p.morningTime).put("eveningTime", p.eveningTime).put("followupHours", p.followupHours),
                    p.authToken,
                )

                val tasks = repo.changedTasks(p.pushSince)
                val cats = repo.changedCategories(p.pushSince)
                val sessions = repo.allSessions() // immutable on the server, so re-sending is harmless
                val body = JSONObject().put("since", p.pullSince)
                    .put("tasks", JSONArray(tasks.map { it.toJson() }))
                    .put("categories", JSONArray(cats.map { it.toJson() }))
                    .put("sessions", JSONArray(sessions.map { it.toJson() }))
                val res = Http.request("POST", "${p.serverUrl}/sync", body, p.authToken)

                val pulled = repo.applyRemote(
                    res.getJSONArray("tasks").mapObjects { it.toTask() },
                    res.getJSONArray("categories").mapObjects { it.toCategory() },
                    res.getJSONArray("sessions").mapObjects { it.toSession() },
                )
                prefs.setCursors(pull = res.getLong("serverTime"), push = started)
                SyncResult.Success(tasks.size + cats.size, pulled)
            } catch (e: HttpStatusException) {
                if (e.code == 401) { prefs.clearAuth(); SyncResult.Error("Session expired. Please sign in again.") }
                else SyncResult.Error(e.message ?: "Server error")
            } catch (e: Exception) {
                SyncResult.Error(e.message ?: "Sync failed")
            }
        }
    }
}
