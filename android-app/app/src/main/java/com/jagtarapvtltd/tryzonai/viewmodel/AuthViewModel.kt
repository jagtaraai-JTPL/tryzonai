package com.jagtarapvtltd.tryzonai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagtarapvtltd.tryzonai.network.LoginRequest
import com.jagtarapvtltd.tryzonai.network.RegisterRequest
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import com.jagtarapvtltd.tryzonai.network.UserResponse
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

class AuthViewModel(private val sessionManager: com.jagtarapvtltd.tryzonai.utils.SessionManager) : ViewModel() {
    
    private val _user = MutableStateFlow<UserResponse?>(null)
    val user: StateFlow<UserResponse?> = _user.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        checkPersistedSession()
    }

    private fun checkPersistedSession() {
        viewModelScope.launch {
            // Use `first()` to get the current token ONCE on startup.
            // Using `collect{}` caused a loop: 401 → logout → clearSession → Flow emits null → 
            // next re-login → Flow emits token → refresh() → 401 → repeat.
            val token = sessionManager.authToken.first()
            if (token != null) {
                RetrofitClient.setAuthToken(token)
                registerDeviceToken()
                // Fetch user profile to verify token
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val userResponse = RetrofitClient.apiService.getMe()
                _user.value = userResponse
                val prefs = sessionManager.context.getSharedPreferences("tryzon_user_prefs", Context.MODE_PRIVATE)
                val tryOnPrefs = sessionManager.context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("user_paid_credits_balance", userResponse.paid_credits)
                    .putBoolean("is_premium_subscriber", userResponse.is_premium || (userResponse.subscription_tier != null && userResponse.subscription_tier != "free"))
                    .apply()
                if (userResponse.has_given_5_star) {
                    tryOnPrefs.edit().putBoolean("has_given_5_star", true).apply()
                }
            } catch (e: Exception) {
                // If it's a 401, we should logout
                if (e is HttpException && e.code() == 401) {
                    logout()
                }
            }
        }
    }

    fun updateProfile(
        hasGiven5Star: Boolean? = null,
        name: String? = null,
        prefGender: String? = null,
        prefFashionGoal: String? = null,
        prefStyleVibe: String? = null
    ) {
        viewModelScope.launch {
            try {
                val updatedUser = RetrofitClient.apiService.updateProfile(
                    com.jagtarapvtltd.tryzonai.network.ProfileUpdateRequest(
                        name = name,
                        has_given_5_star = hasGiven5Star,
                        pref_gender = prefGender,
                        pref_fashion_goal = prefFashionGoal,
                        pref_style_vibe = prefStyleVibe
                    )
                )
                _user.value = updatedUser
                if (hasGiven5Star == true) {
                    val tryOnPrefs = sessionManager.context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
                    tryOnPrefs.edit().putBoolean("has_given_5_star", true).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed to update profile", e)
            }
        }
    }

    fun submitRating(stars: Int, comment: String? = null) {
        viewModelScope.launch {
            try {
                val updatedUser = RetrofitClient.apiService.rateApp(
                    com.jagtarapvtltd.tryzonai.network.RateRequest(stars = stars, comment = comment)
                )
                _user.value = updatedUser
                if (stars == 5) {
                    val tryOnPrefs = sessionManager.context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
                    tryOnPrefs.edit().putBoolean("has_given_5_star", true).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed to submit rating", e)
            }
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = RetrofitClient.apiService.login(LoginRequest(email, pass))
                RetrofitClient.setAuthToken(response.token)
                sessionManager.saveAuthToken(response.token)

                val userPrefs = sessionManager.context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE)
                userPrefs.edit()
                    .putInt("user_paid_credits_balance", response.user.paid_credits)
                    .putBoolean("is_premium_subscriber", response.user.is_premium || (response.user.subscription_tier != null && response.user.subscription_tier != "free"))
                    .apply()

                _user.value = response.user
                viewModelScope.launch(Dispatchers.IO) {
                    try { registerDeviceToken() } catch (_: Exception) {}
                }
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _error.value = "Auth Error: ${parseErrorMessage(errorBody)}"
            } catch (e: Exception) {
                _error.value = "Connection Error: Please check your internet."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(name: String, email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = RetrofitClient.apiService.register(RegisterRequest(email, name, pass))
                RetrofitClient.setAuthToken(response.token)
                sessionManager.saveAuthToken(response.token)

                val userPrefs = sessionManager.context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE)
                userPrefs.edit()
                    .putInt("user_paid_credits_balance", response.user.paid_credits)
                    .putBoolean("is_premium_subscriber", response.user.is_premium || (response.user.subscription_tier != null && response.user.subscription_tier != "free"))
                    .apply()

                _user.value = response.user
                viewModelScope.launch(Dispatchers.IO) {
                    try { registerDeviceToken() } catch (_: Exception) {}
                }
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _error.value = "Registration Error: ${parseErrorMessage(errorBody)}"
            } catch (e: Exception) {
                _error.value = "Connection Error: Server is unreachable."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginWithGoogle(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = RetrofitClient.apiService.googleLogin(com.jagtarapvtltd.tryzonai.network.GoogleLoginRequest(idToken = token))
                RetrofitClient.setAuthToken(response.token)
                sessionManager.saveAuthToken(response.token)
                _user.value = response.user
                try {
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.setUserId(response.user.id.toString())
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.setUserProperty("subscription_tier", response.user.subscription_tier ?: "free")
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.setUserProperty("paid_credits", response.user.paid_credits.toString())
                } catch (_: Exception) {}
                viewModelScope.launch(Dispatchers.IO) {
                    try { registerDeviceToken() } catch (_: Exception) {}
                    try { com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.autoSyncSessionManager(sessionManager.context, sessionManager) } catch (_: Exception) {}
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _error.value = "Google Login Error: ${parseErrorMessage(errorBody)}"
            } catch (e: Exception) {
                _error.value = "Google Auth Failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = RetrofitClient.apiService.deleteAccount()
                if (response.isSuccessful) {
                    logout()
                } else {
                    _error.value = "Failed to delete account: ${response.message()}"
                }
            } catch (e: Exception) {
                _error.value = "Error deleting account: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun triggerGoogleSignIn(context: Context) {
        _error.value = null
        val activity = context as? android.app.Activity ?: context.findActivity()
        if (activity == null) {
            android.util.Log.e("AuthViewModel", "Activity context is null")
            _error.value = "Error: Activity context required"
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            try {
                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("190030026417-g88ivnm5hoemljrl6knm8t1hn1c0h1sl.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                android.util.Log.d("AuthViewModel", "Requesting credential instantly from CredentialManager")
                val result = credentialManager.getCredential(
                    context = activity,
                    request = request
                )
                android.util.Log.d("AuthViewModel", "Credential received: ${result.credential.javaClass.simpleName}")
                
                val credential = result.credential
                var googleIdToken: String? = null
                
                if (credential is androidx.credentials.CustomCredential && credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    googleIdToken = googleIdTokenCredential.idToken
                } else if (credential is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential) {
                    googleIdToken = credential.idToken
                }
                
                if (googleIdToken != null) {
                    // 1. Exchange Google ID Token for Firebase Token
                    val firebaseAuth = FirebaseAuth.getInstance()
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    
                    try {
                        android.util.Log.d("AuthViewModel", "Signing into Firebase with Google Credential")
                        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                        val firebaseUser = authResult.user
                        android.util.Log.d("AuthViewModel", "Firebase sign-in successful: ${firebaseUser?.uid}")
                        
                        // 2. Get cached Firebase ID Token (Fast, non-blocking)
                        val firebaseIdTokenResult = firebaseUser?.getIdToken(false)?.await()
                        val firebaseIdToken = firebaseIdTokenResult?.token
                        
                        if (firebaseIdToken != null) {
                            val response = RetrofitClient.apiService.googleLogin(com.jagtarapvtltd.tryzonai.network.GoogleLoginRequest(idToken = firebaseIdToken))
                            RetrofitClient.setAuthToken(response.token)
                            sessionManager.saveAuthToken(response.token)

                            val userPrefs = sessionManager.context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE)
                            userPrefs.edit()
                                .putInt("user_paid_credits_balance", response.user.paid_credits)
                                .putBoolean("is_premium_subscriber", response.user.is_premium || (response.user.subscription_tier != null && response.user.subscription_tier != "free"))
                                .apply()

                            _user.value = response.user
                            viewModelScope.launch(Dispatchers.IO) {
                                try { registerDeviceToken() } catch (_: Exception) {}
                            }
                        } else {
                            _error.value = "Failed to retrieve Firebase Token"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Firebase Sign-In Failed", e)
                        _error.value = "Firebase Sign-In Failed: ${e.localizedMessage}"
                    }
                } else {
                    android.util.Log.e("AuthViewModel", "Credential is not GoogleIdTokenCredential: ${credential.javaClass.simpleName}")
                    _error.value = "Received wrong credential type"
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                android.util.Log.d("AuthViewModel", "User cancelled Google Sign-In sheet")
                _error.value = null
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                android.util.Log.d("AuthViewModel", "NoCredentialException: User cancelled or dismissed picker")
                _error.value = null
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                android.util.Log.e("AuthViewModel", "GetCredentialException", e)
                _error.value = null
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "General Exception in triggerGoogleSignIn", e)
                _error.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseErrorMessage(errorBody: String?): String {
        return try {
            // Simple extraction of "detail" field from FastAPI error JSON
            if (errorBody?.contains("\"detail\":\"") == true) {
                errorBody.substringAfter("\"detail\":\"").substringBefore("\"")
            } else {
                "Unknown error occurred."
            }
        } catch (e: Exception) {
            "Error parsing server response."
        }
    }

    fun logout() {
        viewModelScope.launch {
            RetrofitClient.setAuthToken(null)
            sessionManager.clearSession()
            _user.value = null
        }
    }

    fun isLoggedIn(): Boolean = _user.value != null

    fun registerDeviceToken() {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                RetrofitClient.apiService.registerDevice(com.jagtarapvtltd.tryzonai.network.DeviceRegisterRequest(fcm_token = token))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
