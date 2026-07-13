package com.hanamobile.domain.service.inference

import com.hanamobile.core.model.BackendException
import com.hanamobile.core.model.GenerationConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationConfigValidatorTest {

    @Test
    fun acceptsValidGenerationSettings() {
        val config = GenerationConfig(
            maxTokens = 128,
            topK = 20,
            topP = 0.9f,
            temperature = 0.7f,
            randomSeed = 42
        )

        val sampler = GenerationConfigValidator.toSamplerConfig(config)

        assertEquals(128, sampler.maxTokens)
        assertEquals(20, sampler.topK)
        assertEquals(0.9f, sampler.topP)
        assertEquals(0.7f, sampler.temperature)
        assertEquals(42L, sampler.randomSeed)
    }

    @Test(expected = BackendException::class)
    fun failsEarlyOnInvalidRandomSeed() {
        GenerationConfigValidator.validate(
            GenerationConfig(randomSeed = -1)
        )
    }
}
