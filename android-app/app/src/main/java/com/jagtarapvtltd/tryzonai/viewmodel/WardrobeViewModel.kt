package com.jagtarapvtltd.tryzonai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem

class WardrobeViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = com.jagtarapvtltd.tryzonai.utils.SessionManager(application.applicationContext)
    
    private val _savedTryOns = MutableStateFlow<List<TryOnHistoryItem>>(emptyList())
    val savedTryOns: StateFlow<List<TryOnHistoryItem>> = _savedTryOns.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadWardrobe()
    }

    fun loadWardrobe() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Background sync from Backend & Cloud Vault
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try { com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.autoSyncSessionManager(getApplication(), sessionManager) } catch (_: Exception) {}
                }
                // Collect local history
                sessionManager.localHistory.collect { localList ->
                    _savedTryOns.value = localList.sortedByDescending { it.timestamp }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun saveItem(item: Any) {
        if (item is com.jagtarapvtltd.tryzonai.models.TryOnResult) {
            viewModelScope.launch {
                try {
                    RetrofitClient.apiService.saveToWardrobe(item.resultImage)
                    loadWardrobe()
                } catch (e: Exception) {
                    android.util.Log.e("TryZonAPI", "Save to Wardrobe Failed: ${e.message}")
                }
            }
        }
    }
    fun deleteItem(sessionId: String) {
        viewModelScope.launch {
            sessionManager.removeLocalHistoryItem(sessionId)
            val current = _savedTryOns.value.toMutableList()
            current.removeAll { it.id == sessionId }
            _savedTryOns.value = current
            try {
                RetrofitClient.apiService.deleteTryOnHistoryItem(sessionId)
            } catch (e: Exception) {
                android.util.Log.e("WardrobeViewModel", "Remote delete failed: ${e.message}")
            }
        }
    }
}
