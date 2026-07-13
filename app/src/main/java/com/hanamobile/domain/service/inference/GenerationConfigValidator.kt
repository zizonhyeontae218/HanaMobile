package com.hanamobile.domain.service.inference

import com.google.ai.edge.litertlm.SamplerConfig
import com.hanamobile.core.model.BackendError
import com.hanamobile.core.model.BackendException
import com.hanamobile.core.model.GenerationConfig

internal object GenerationConfigValidator {
    fun validate(config: GenerationConfig) {
        requireSetting(config.maxTokens > 0, "maxTokens must be > 0")
        requireSetting(config.topK > 0, "topK must be > 0")
        requireSetting(config.topP in 0f..1f, "topP must be in [0, 1]")
        requireSetting(config.temperature >= 0f, "temperature must be >= 0")
        requireSetting(config.randomSeed >= 0, "randomSeed must be >= 0")
    }

    fun toSamplerConfig(config: GenerationConfig): SamplerConfig {
        validate(config)
        return SamplerConfig(
            topK = config.topK,
            topP = config.topP,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            randomSeed = config.randomSeed.toLong()
        )
    }

    private fun requireSetting(valid: Boolean, message: String) {
        if (!valid) throw BackendException(BackendError.UnsupportedSetting(message))
    }
}
