package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.ai.GeminiRepository
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager

class HomeViewModelFactory(
    private val dao: BibleDao,
    private val preferences: AppPreferences,
    private val geminiRepository: GeminiRepository,
    private val syncManager: SyncManager? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(dao, preferences) as T
        }
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            // FIXED: must pass syncManager, else ChatViewModel runs local-only
            // with syncManager = null and cloud sync never happens
            return ChatViewModel(dao, geminiRepository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}