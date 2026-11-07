package com.nicolo.appstudio

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "study_preferences")
val STUDIED_DAYS_KEY = stringSetPreferencesKey("studied_days")
val STUDY_VIDEO_IDS_KEY = stringSetPreferencesKey("study_video_ids")
val RESET_STUDIED_DAYS_KEY = booleanPreferencesKey("has_reset_studied_days_for_videos")
val DAY_DESCRIPTIONS_KEY = stringPreferencesKey("day_descriptions")
val CUSTOM_VIDEOS_KEY = stringPreferencesKey("custom_videos_by_date")

data class CameraVideo(
    val id: Long,
    val uri: Uri,
    val date: LocalDate,
    val displayName: String
)

data class DayVideoItem(
    val id: String,
    val uri: Uri,
    val date: LocalDate,
    val displayName: String,
    val isCustom: Boolean = false
)

fun mapToJson(map: Map<String, String>): String {
    val json = JSONObject()
    map.forEach { (k, v) ->
        if (v.isNotBlank()) {
            json.put(k, v)
        }
    }
    return json.toString()
}

fun jsonToMap(jsonStr: String?): Map<String, String> {
    if (jsonStr.isNullOrBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    try {
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key, "")
            if (value.isNotBlank()) {
                map[key] = value
            }
        }
    } catch (_: Exception) {
    }
    return map
}

fun customVideosMapToJson(map: Map<String, List<String>>): String {
    val json = JSONObject()
    map.forEach { (k, v) ->
        if (v.isNotEmpty()) {
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            json.put(k, arr)
        }
    }
    return json.toString()
}

fun jsonToCustomVideosMap(jsonStr: String?): Map<String, List<String>> {
    if (jsonStr.isNullOrBlank()) return emptyMap()
    val map = mutableMapOf<String, List<String>>()
    try {
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = json.optJSONArray(key)
            if (arr != null) {
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val uriStr = arr.optString(i, "")
                    if (uriStr.isNotBlank() && !list.contains(uriStr)) {
                        list.add(uriStr)
                    }
                }
                if (list.isNotEmpty()) {
                    map[key] = list
                }
            }
        }
    } catch (_: Exception) {
    }
    return map
}
