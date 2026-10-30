package com.nastya.diary.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupExporter(private val context: Context) {

    suspend fun write(json: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val file = File(directory, FILE_NAME)
        file.writeText(json)
        file
    }

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val FILE_NAME = "mood_diary_backup.json"
    }
}
