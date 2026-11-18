package com.bolo.english.progress

import kotlinx.serialization.json.Json
import java.io.File

/** Saves progress as JSON in app-private storage. A missing or damaged file starts fresh. */
class ProgressRepository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): Progress = runCatching {
        if (!file.exists()) Progress() else json.decodeFromString(Progress.serializer(), file.readText())
    }.getOrDefault(Progress())

    fun save(p: Progress) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Progress.serializer(), p))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
