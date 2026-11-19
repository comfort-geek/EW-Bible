package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.ChatDao
import com.ewbible.app.data.database.GeminiRepository
import com.ewbible.app.data.preferences.AppPreferences

class HomeViewModelFactory(
    private val dao: BibleDao,
    private val preferences: AppPreferences,
    private val chatDao: ChatDao? = null,
    private val geminiRepository: GeminiRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(dao, preferences) as T
        }
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            requireNotNull(chatDao) { "ChatDao not provided to factory" }
            requireNotNull(geminiRepository) { "GeminiRepository not provided to factory" }
            return ChatViewModel(chatDao, geminiRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}