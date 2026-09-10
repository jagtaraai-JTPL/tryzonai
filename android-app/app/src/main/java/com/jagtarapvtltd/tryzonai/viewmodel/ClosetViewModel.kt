package com.jagtarapvtltd.tryzonai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import com.jagtarapvtltd.tryzonai.network.WardrobeItem

class ClosetViewModel(application: Application) : AndroidViewModel(application) {
    private val _closetItems = MutableStateFlow<List<WardrobeItem>>(emptyList())
    val closetItems: StateFlow<List<WardrobeItem>> = _closetItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadCloset()
    }

    fun loadCloset() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val remoteCloset = RetrofitClient.apiService.getWardrobe()
                _closetItems.value = remoteCloset.sortedByDescending { it.created_at }
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Closet Load Failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveToCloset(imageUrl: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.saveToWardrobe(imageUrl)
                loadCloset() // Refresh
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Save to Closet Failed: ${e.message}")
            }
        }
    }

    fun removeFromCloset(itemId: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.removeWardrobeItem(itemId)
                val current = _closetItems.value.toMutableList()
                current.removeAll { it.id == itemId }
                _closetItems.value = current
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Delete from Closet Failed: ${e.message}")
            }
        }
    }
}
