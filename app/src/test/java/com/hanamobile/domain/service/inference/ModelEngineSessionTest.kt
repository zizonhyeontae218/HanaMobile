package com.hanamobile.domain.service.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEngineSessionTest {

    @Test
    fun recreatesEngineWhenModelPathChanges() {
        val session = ModelEngineSession<String, FakeEngine>()
        val first = FakeEngine("first")
        val second = FakeEngine("second")

        session.swap("/models/a.litertlm", first)
        session.swap("/models/b.litertlm", second)

        assertTrue(first.closed)
        assertSame(second, session.currentOrNull("/models/b.litertlm"))
    }

    @Test
    fun reusesEngineForSamePath() {
        val session = ModelEngineSession<String, FakeEngine>()
        val first = FakeEngine("first")
        session.swap("/models/a.litertlm", first)

        assertSame(first, session.currentOrNull("/models/a.litertlm"))
    }

    @Test
    fun closesCurrentEngineOnClose() {
        val session = ModelEngineSession<String, FakeEngine>()
        val engine = FakeEngine("engine")
        session.swap("/models/a.litertlm", engine)

        session.closeCurrent()

        assertTrue(engine.closed)
        assertEquals(null, session.currentOrNull("/models/a.litertlm"))
    }
}

private class FakeEngine(private val name: String) : AutoCloseable {
    var closed: Boolean = false

    override fun close() {
        closed = true
    }

    override fun toString(): String = name
}
