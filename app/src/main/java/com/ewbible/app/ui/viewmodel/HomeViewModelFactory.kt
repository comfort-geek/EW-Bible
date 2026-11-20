package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.GeminiRepository
import com.ewbible.app.data.preferences.AppPreferences

class HomeViewModelFactory(
    private val dao: BibleDao,
    private val preferences: AppPreferences,
    private val geminiRepository: GeminiRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(dao, preferences) as T
        }
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            // FIXED: ChatViewModel now takes (bibleDao, geminiRepository) only
            // ChatDao deleted - chats are in BibleDao, dao IS the BibleDao
            return ChatViewModel(dao, geminiRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}