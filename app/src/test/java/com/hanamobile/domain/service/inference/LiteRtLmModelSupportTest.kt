package com.hanamobile.domain.service.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmModelSupportTest {

    @Test
    fun onlyLitertlmExtensionIsAccepted() {
        assertTrue(LiteRtLmModelSupport.isSupportedExtension("model.litertlm"))
        assertFalse(LiteRtLmModelSupport.isSupportedExtension("model.task"))
        assertFalse(LiteRtLmModelSupport.isSupportedExtension("model.bin"))
    }

    @Test
    fun unsafePathTraversalPatternsAreRejected() {
        assertFalse(LiteRtLmModelSupport.isSafeFileName("../model.litertlm"))
        assertFalse(LiteRtLmModelSupport.isSafeFileName("folder/model.litertlm"))
        assertFalse(LiteRtLmModelSupport.isSafeFileName("..\\model.litertlm"))
    }
}
