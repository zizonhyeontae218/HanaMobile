package com.hanamobile.app

import android.app.Application
import androidx.room.Room
import com.hanamobile.core.session.SessionManager
import com.hanamobile.data.local.HanaDatabase
import com.hanamobile.data.repository.ChatSessionRepositoryImpl
import com.hanamobile.data.repository.MemoryRepositoryImpl
import com.hanamobile.data.repository.PromptRepositoryImpl
import com.hanamobile.domain.service.MemoryManager
import com.hanamobile.domain.service.MockSpeechToTextEngine
import com.hanamobile.domain.service.MockTextToSpeechEngine
import com.hanamobile.domain.service.SimpleWaveformAnimator
import com.hanamobile.domain.service.inference.LocalModelCatalog

class HanaApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, HanaDatabase::class.java, "hana.db").build()
        val promptRepository = PromptRepositoryImpl(db.promptDao())
        val memoryRepository = MemoryRepositoryImpl(db.memoryDao())
        val sessionRepository = ChatSessionRepositoryImpl(db.chatDao())
        val memoryManager = MemoryManager()
        val modelCatalog = LocalModelCatalog(applicationContext)

        val localInferenceBackend = LiteRtLmBackendProvider(
            modelCatalog = modelCatalog,
            promptRepository = promptRepository
        ).createBackend()

        container = AppContainer(
            promptRepository = promptRepository,
            memoryRepository = memoryRepository,
            chatSessionRepository = sessionRepository,
            sessionManager = SessionManager(localInferenceBackend, memoryManager),
            memoryManager = memoryManager,
            stt = MockSpeechToTextEngine(),
            tts = MockTextToSpeechEngine(),
            waveformAnimator = SimpleWaveformAnimator(),
            modelCatalog = modelCatalog
        )
    }
}
