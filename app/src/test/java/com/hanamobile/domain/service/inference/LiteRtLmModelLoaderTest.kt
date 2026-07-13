package com.hanamobile.domain.service.inference

import com.hanamobile.core.model.BackendConfig
import com.hanamobile.core.model.BackendException
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmModelLoaderTest {

    @Test(expected = BackendException::class)
    fun blocksPathTraversalSelection() {
        val root = createTempDirectory("models").toFile()
        try {
            val config = BackendConfig(
                modelDirectoryPath = root.absolutePath,
                defaultModelFileName = "model.litertlm"
            )
            File(root, "model.litertlm").writeText("ok")

            LiteRtLmModelLoader(config).resolveModelFile("../evil.litertlm")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolvesValidLitertlmModel() {
        val root = createTempDirectory("models").toFile()
        try {
            val model = File(root, "good-model.litertlm").apply { writeText("ok") }
            val config = BackendConfig(
                modelDirectoryPath = root.absolutePath,
                defaultModelFileName = model.name
            )

            val resolved = LiteRtLmModelLoader(config).resolveModelFile(model.name)

            assertEquals(model.canonicalPath, resolved.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
