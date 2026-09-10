package com.jagtarapvtltd.tryzonai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagtarapvtltd.tryzonai.models.Product
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import com.jagtarapvtltd.tryzonai.network.StylistRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Message(
    val content: String,
    val isUser: Boolean,
    val recommendations: List<Product>? = null
)

class StylistViewModel : ViewModel() {
    
    private val _messages = MutableStateFlow<List<Message>>(listOf(
        Message("Hello! I am TiTi, your AI Stylist. Aap mujhse fashion advice le sakte hain. Aaj kya pehnna chahenge?", false)
    ))
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val chatHistory = mutableListOf<Map<String, String>>()

    fun sendMessage(text: String, includeWardrobe: Boolean = false) {
        if (text.isBlank()) return
        
        val userMsg = Message(text, true)
        _messages.value = _messages.value + userMsg
        chatHistory.add(mapOf("role" to "user", "content" to text))
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.sendMessage(
                    StylistRequest(
                        message = text,
                        history = chatHistory
                    )
                )
                
                val titiMsg = Message(
                    content = response.response,
                    isUser = false,
                    recommendations = response.recommendations
                )
                _messages.value = _messages.value + titiMsg
                chatHistory.add(mapOf("role" to "assistant", "content" to response.response))
                
            } catch (e: Exception) {
                val errorMsg = Message("Maaf kijiye, abhi TiTi thoda busy hai. Please try again later.", false)
                _messages.value = _messages.value + errorMsg
            } finally {
                _isLoading.value = false
            }
        }
    }
}
