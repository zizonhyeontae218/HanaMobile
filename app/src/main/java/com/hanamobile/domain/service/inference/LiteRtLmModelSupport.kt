package com.hanamobile.domain.service.inference

import com.hanamobile.core.model.BackendError
import com.hanamobile.core.model.BackendException

internal object LiteRtLmModelSupport {
    val supportedExtensions: Set<String> = setOf("litertlm")

    // Intentionally restrictive: only plain filenames are accepted.
    // This blocks separators/traversal patterns and keeps model selection UI-safe.
    private val fileNamePattern = Regex("^[A-Za-z0-9._-]+$")

    fun requireSafeFileName(fileName: String) {
        if (!isSafeFileName(fileName)) {
            throw BackendException(BackendError.InvalidModelSelection(fileName))
        }
    }

    fun requireSupportedExtension(fileNameOrPath: String) {
        if (!isSupportedExtension(fileNameOrPath)) {
            throw BackendException(BackendError.UnsupportedModelFile(fileNameOrPath))
        }
    }

    fun isSafeFileName(fileName: String): Boolean =
        fileNamePattern.matches(fileName) &&
            !fileName.startsWith(".") &&
            '/' !in fileName &&
            '\\' !in fileName

    fun isSupportedExtension(fileName: String): Boolean =
        fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase() in supportedExtensions

    fun supportedExtensionsLabel(): String =
        supportedExtensions.joinToString(prefix = ".", separator = ", .")
}
