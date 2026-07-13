package com.hanamobile.core.extensions

import com.hanamobile.core.model.BackendRequest
import com.hanamobile.core.model.BackendResponse
import com.hanamobile.core.model.MultimodalPayload
import com.hanamobile.core.model.SoulInterventionNote
import com.hanamobile.core.model.ToolResult
import kotlinx.coroutines.flow.Flow

interface LocalInferenceBackend {
    suspend fun generate(request: BackendRequest): BackendResponse
}

interface StreamingLocalInferenceBackend : LocalInferenceBackend {
    fun generateStream(request: BackendRequest): Flow<String>
}

interface SpeechToTextEngine {
    suspend fun startListening(onPartial: (String) -> Unit)
    suspend fun stopListening(): String
    suspend fun cancel()
}

interface TextToSpeechEngine {
    suspend fun speak(text: String)
    suspend fun stop()
    fun amplitudeFlow(): Flow<Float>
}

interface WaveformAnimator {
    fun bindAmplitude(amplitude: Flow<Float>): Flow<List<Float>>
}

interface ImageInputSource {
    suspend fun pickImageUri(): String?
}

interface ImagePreprocessor {
    suspend fun prepare(uri: String): ByteArray
}

interface MultimodalRequestPackager {
    suspend fun buildPayload(imageUris: List<String>): MultimodalPayload
}

interface ToolRegistry {
    fun availableTools(): List<ToolProvider>
}

interface ToolProvider {
    val name: String
    suspend fun invoke(query: String): ToolResult
}

interface ExternalResultFormatter {
    fun format(results: List<ToolResult>): String
}

interface SoulEngineIntervention {
    suspend fun beforeGeneration(request: BackendRequest): BackendRequest
    suspend fun reviewResponse(request: BackendRequest, response: BackendResponse): SoulInterventionNote?
}
