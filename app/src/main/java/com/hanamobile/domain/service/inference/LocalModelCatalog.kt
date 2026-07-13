package com.hanamobile.domain.service.inference

import android.content.Context
import java.io.File

class LocalModelCatalog(private val context: Context) {

    fun modelDirectory(): File {
        val mediaRoot = context.externalMediaDirs.firstOrNull()
        val dir = File(mediaRoot ?: context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listModelFiles(): List<File> = modelDirectory()
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .filter { LiteRtLmModelSupport.isSafeFileName(it.name) }
        .filter { LiteRtLmModelSupport.isSupportedExtension(it.name) }
        .sortedBy { it.name.lowercase() }
}
