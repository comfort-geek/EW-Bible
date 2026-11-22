package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.ai.AiRepository
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.sync.SyncManager

class ChatViewModelFactory(
    private val dao: BibleDao,
    private val aiRepository: AiRepository,
    private val syncManager: SyncManager? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                bibleDao = dao,
                geminiRepository = aiRepository,
                syncManager = syncManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}