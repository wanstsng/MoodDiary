package com.nastya.diary.utils

import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.model.Mood
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class ParsedBackupEntry(
    val title: String,
    val content: String,
    val date: LocalDate,
    val mood: Mood,
    val categoryName: String,
    val categoryColor: String,
    val isFavorite: Boolean,
    val createdAt: Long
)

data class ParsedBackup(
    val entries: List<ParsedBackupEntry>
)

object BackupCodec {

    private const val VERSION = 1

    fun encode(entries: List<DiaryEntry>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val entriesArray = JSONArray()
        entries.forEach { entry ->
            entriesArray.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("content", entry.content)
                    put("date", entry.date.toString())
                    put("mood", entry.mood.name)
                    put("categoryName", entry.category.name)
                    put("categoryColor", entry.category.color)
                    put("isFavorite", entry.isFavorite)
                    put("createdAt", entry.createdAt)
                }
            )
        }
        root.put("entries", entriesArray)

        return root.toString(2)
    }

    fun decode(raw: String): ParsedBackup {
        val root = JSONObject(raw)
        val entriesJson = root.getJSONArray("entries")

        val entries = (0 until entriesJson.length()).map { index ->
            val obj = entriesJson.getJSONObject(index)
            ParsedBackupEntry(
                title = obj.getString("title"),
                content = obj.optString("content", ""),
                date = LocalDate.parse(obj.getString("date")),
                mood = runCatching { Mood.valueOf(obj.getString("mood")) }.getOrDefault(Mood.NEUTRAL),
                categoryName = obj.getString("categoryName"),
                categoryColor = obj.optString("categoryColor", Category.DEFAULT_COLOR),
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }

        return ParsedBackup(entries = entries)
    }
}
