package com.garetto.jukebox.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

data class UserState(
    val favorites: Set<Long> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    /** How many times each song has been listened to. */
    val plays: Map<Long, Int> = emptyMap(),
)

/** Favorites, playlists and play counts. Saved on the phone automatically. */
object UserData {
    private const val KEY = "user_data"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(UserState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("jukebox_user", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { raw ->
            try {
                _state.value = fromJson(raw)
            } catch (e: Exception) {
            }
        }
    }

    private fun change(f: (UserState) -> UserState) {
        val next = f(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs?.edit()?.putString(KEY, toJson(next))?.apply()
    }

    fun toggleFavorite(songId: Long) = change {
        it.copy(favorites = if (songId in it.favorites) it.favorites - songId else it.favorites + songId)
    }

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        val clean = name.trim().ifEmpty { "New playlist" }
        change { it.copy(playlists = it.playlists + Playlist(id, clean, emptyList())) }
        return id
    }

    fun deletePlaylist(id: Long) = change { s ->
        s.copy(playlists = s.playlists.filter { it.id != id })
    }

    fun addToPlaylist(id: Long, songId: Long) = change { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == id && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        })
    }

    fun removeFromPlaylist(id: Long, songId: Long) = change { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == id) it.copy(songIds = it.songIds - songId) else it
        })
    }

    fun bumpPlay(songId: Long) = change {
        it.copy(plays = it.plays + (songId to ((it.plays[songId] ?: 0) + 1)))
    }

    private fun toJson(s: UserState): String {
        val lists = JSONArray()
        s.playlists.forEach { p ->
            lists.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        val plays = JSONObject()
        s.plays.forEach { (k, v) -> plays.put(k.toString(), v) }
        return JSONObject()
            .put("favorites", JSONArray(s.favorites.toList()))
            .put("playlists", lists)
            .put("plays", plays)
            .toString()
    }

    private fun longs(a: JSONArray?): List<Long> {
        if (a == null) return emptyList()
        return List(a.length()) { a.optLong(it) }
    }

    private fun fromJson(raw: String): UserState {
        val o = JSONObject(raw)
        val lists = ArrayList<Playlist>()
        o.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.optJSONObject(i) ?: continue
                lists.add(Playlist(p.optLong("id"), p.optString("name", "Playlist"), longs(p.optJSONArray("songs"))))
            }
        }
        val plays = HashMap<Long, Int>()
        o.optJSONObject("plays")?.let { p ->
            val keys = p.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                plays[id] = p.optInt(k, 0)
            }
        }
        return UserState(longs(o.optJSONArray("favorites")).toSet(), lists, plays)
    }
}
