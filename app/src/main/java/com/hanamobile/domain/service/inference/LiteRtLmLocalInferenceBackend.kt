package com.hanamobile.domain.service.inference

import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.hanamobile.core.extensions.StreamingLocalInferenceBackend
import com.hanamobile.core.model.BackendConfig
import com.hanamobile.core.model.BackendError
import com.hanamobile.core.model.BackendException
import com.hanamobile.core.model.BackendRequest
import com.hanamobile.core.model.BackendResponse
import com.hanamobile.core.model.RuntimeConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real on-device backend using official LiteRT-LM Android Kotlin APIs.
 */
class LiteRtLmLocalInferenceBackend(
    private val config: BackendConfig,
    private val selectedModelProvider: suspend () -> String?,
    private val promptFormatter: LiteRtLmPromptFormatter = LiteRtLmPromptFormatter(),
    private val modelLoader: LiteRtLmModelLoader = LiteRtLmModelLoader(config),
    private val engineFactory: LiteRtLmEngineFactory = LiteRtLmEngineFactory.Default,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : StreamingLocalInferenceBackend {

    private val initLock = Mutex()
    private val engineSession = ModelEngineSession<EngineRuntimeSignature, Engine>()

    override suspend fun generate(request: BackendRequest): BackendResponse {
        val chunks = mutableListOf<String>()
        generateStream(request).collect { chunk -> chunks += chunk }
        val output = chunks.joinToString(separator = "").trim()
        if (output.isBlank()) {
            throw BackendException(BackendError.GenerationFailure("Empty response from LiteRT-LM runtime"))
        }

        val modelFile = currentModelFile()
        return BackendResponse(
            text = output,
            diagnostics = mapOf(
                "backend" to config.backendId,
                "modelPath" to modelFile.absolutePath,
                "modelFile" to modelFile.name
            )
        )
    }

    override fun generateStream(request: BackendRequest): Flow<String> = flow {
        val modelFile = currentModelFile()
        val runtimeSignature = EngineRuntimeSignature(
            canonicalModelPath = modelFile.absolutePath,
            runtimeConfig = config.runtime
        )
        val engine = ensureEngineInitialized(runtimeSignature)
        val prompt = promptFormatter.format(request)

        val conversationConfig = ConversationConfig(
            samplerConfig = GenerationConfigValidator.toSamplerConfig(config.generation)
        )

        try {
            engine.createConversation(conversationConfig).use { conversation ->
                conversation.sendMessageAsync(prompt)
                    .map { it.text }
                    .collect { chunk ->
                        if (chunk.isNotEmpty()) emit(chunk)
                    }
            }
        } catch (e: BackendException) {
            throw e
        } catch (e: Throwable) {
            throw mapGenerationError(e)
        }
    }.catch { throwable ->
        throw if (throwable is BackendException) throwable else mapGenerationError(throwable)
    }

    suspend fun close() {
        initLock.withLock {
            runCatching { engineSession.closeCurrent() }
                .getOrElse { throw mapLifecycleError(it) }
        }
    }

    private suspend fun currentModelFile() = withContext(workerDispatcher) {
        val selectedModel = selectedModelProvider()
        modelLoader.resolveModelFile(selectedModel)
    }

    private suspend fun ensureEngineInitialized(signature: EngineRuntimeSignature): Engine {
        // Re-init only when there is no engine yet, or runtime signature changed.
        engineSession.currentOrNull(signature)?.let { return it }

        return initLock.withLock {
            engineSession.currentOrNull(signature)?.let { return@withLock it }

            withContext(workerDispatcher) {
                try {
                    GenerationConfigValidator.validate(config.generation)
                    val engine = engineFactory.create(createEngineConfig(signature))
                    engine.initialize()
                    engineSession.swap(key = signature, newEngine = engine)
                } catch (e: BackendException) {
                    throw e
                } catch (e: Throwable) {
                    throw mapInitializationError(e)
                }
            }
        }
    }

    private fun createEngineConfig(signature: EngineRuntimeSignature): EngineConfig {
        // Note: litertlm-android:0.10.1 in this repo uses modelPath-based EngineConfig construction.
        // executionTarget is tracked in signature/cache keys for future API support, but not yet applied
        // to engine creation arguments in this version.
        return EngineConfig(modelPath = signature.canonicalModelPath)
    }

    private fun mapInitializationError(e: Throwable): BackendException =
        BackendException(
            BackendError.ModelInitializationFailure(e.message ?: "Unknown initialization error", e)
        )

    private fun mapGenerationError(e: Throwable): BackendException =
        BackendException(BackendError.GenerationFailure(e.message ?: "Unknown generation error", e))

    private fun mapLifecycleError(e: Throwable): BackendException =
        BackendException(BackendError.EngineLifecycleFailure(e.message ?: "Unknown lifecycle error", e))
}

private data class EngineRuntimeSignature(
    val canonicalModelPath: String,
    val runtimeConfig: RuntimeConfig
)

fun interface LiteRtLmEngineFactory {
    fun create(config: EngineConfig): Engine

    object Default : LiteRtLmEngineFactory {
        override fun create(config: EngineConfig): Engine = Engine(config)
    }
}
