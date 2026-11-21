package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.ai.AiRepository
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager

class HomeViewModelFactory(
    private val dao: BibleDao,
    private val preferences: AppPreferences,
    // FIXED: AiRepository, not GeminiRepository - matches MainActivity + ChatViewModel
    private val geminiRepository: AiRepository,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                dao = dao,
                preferences = preferences,
                geminiRepository = geminiRepository,
                syncManager = syncManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}