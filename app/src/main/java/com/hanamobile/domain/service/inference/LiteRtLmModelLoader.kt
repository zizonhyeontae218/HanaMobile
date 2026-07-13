package com.hanamobile.domain.service.inference

import com.hanamobile.core.model.BackendConfig
import com.hanamobile.core.model.BackendError
import com.hanamobile.core.model.BackendException
import java.io.File

class LiteRtLmModelLoader(
    private val config: BackendConfig
) {

    fun resolveModelFile(selectedModelFileName: String?): File {
        val modelDir = File(config.modelDirectoryPath)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        if (!modelDir.isDirectory) {
            throw BackendException(BackendError.InvalidModelPath(modelDir.absolutePath))
        }

        val fileName = selectedModelFileName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: config.defaultModelFileName

        LiteRtLmModelSupport.requireSafeFileName(fileName)

        val canonicalModelDir = modelDir.canonicalFile
        val file = File(canonicalModelDir, fileName).canonicalFile
        if (file.parentFile != canonicalModelDir) {
            throw BackendException(BackendError.InvalidModelSelection(fileName))
        }
        if (!file.exists()) {
            throw BackendException(BackendError.ModelFileMissing(file.absolutePath))
        }
        if (!file.isFile) {
            throw BackendException(BackendError.InvalidModelPath(file.absolutePath))
        }

        LiteRtLmModelSupport.requireSupportedExtension(file.name)
        return file
    }
}
