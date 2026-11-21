package com.ewbible.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager

class HomeViewModelFactory(
    private val dao: BibleDao,
    private val preferences: AppPreferences,
    // FIXED: No AiRepository here - HomeViewModel does not take it
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                dao = dao,
                prefs = preferences,
                syncManager = syncManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}