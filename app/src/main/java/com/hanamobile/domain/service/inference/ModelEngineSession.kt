package com.hanamobile.domain.service.inference

/**
 * Reuses a single engine per model path and safely recreates on model switch.
 */
internal class ModelEngineSession<K, T : AutoCloseable> {
    @Volatile
    private var holder: Holder<K, T>? = null

    fun currentOrNull(key: K): T? = holder?.takeIf { it.key == key }?.engine

    fun swap(key: K, newEngine: T): T {
        val previous = holder
        holder = Holder(key = key, engine = newEngine)
        previous?.engine?.close()
        return newEngine
    }

    fun closeCurrent() {
        val existing = holder ?: return
        holder = null
        existing.engine.close()
    }

    private data class Holder<K, T : AutoCloseable>(
        val key: K,
        val engine: T
    )
}
