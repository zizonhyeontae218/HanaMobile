package com.hanamobile.app

import com.hanamobile.BuildConfig
import com.hanamobile.core.model.BackendConfig
import com.hanamobile.core.model.ExecutionTarget
import com.hanamobile.core.model.GenerationConfig
import com.hanamobile.core.model.RuntimeConfig
import com.hanamobile.data.repository.PromptRepositoryImpl
import com.hanamobile.domain.service.inference.LiteRtLmLocalInferenceBackend
import com.hanamobile.domain.service.inference.LocalModelCatalog
import kotlinx.coroutines.flow.first

class LiteRtLmBackendProvider(
    private val modelCatalog: LocalModelCatalog,
    private val promptRepository: PromptRepositoryImpl
) {
    fun createBackend(): LiteRtLmLocalInferenceBackend =
        LiteRtLmLocalInferenceBackend(
            config = createBackendConfig(),
            selectedModelProvider = { promptRepository.observeActiveModelFileName().first() }
        )

    private fun createBackendConfig(): BackendConfig = BackendConfig(
        backendId = "litert-lm",
        modelDirectoryPath = modelCatalog.modelDirectory().absolutePath,
        defaultModelFileName = BuildConfig.LITERT_DEFAULT_MODEL_FILE,
        generation = GenerationConfig(
            maxTokens = BuildConfig.LITERT_MAX_TOKENS,
            topK = BuildConfig.LITERT_TOP_K,
            topP = BuildConfig.LITERT_TOP_P,
            temperature = BuildConfig.LITERT_TEMPERATURE,
            randomSeed = BuildConfig.LITERT_RANDOM_SEED
        ),
        runtime = RuntimeConfig(
            executionTarget = ExecutionTarget.CPU_COMPAT
        )
    )
}
