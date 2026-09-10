package com.jagtarapvtltd.tryzonai.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagtarapvtltd.tryzonai.billing.BillingManager
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState

    private val billingManager = BillingManager(application, viewModelScope)

    init {
        viewModelScope.launch {
            billingManager.purchaseSuccess.collect { purchase ->
                verifyGooglePurchase(purchase.purchaseToken, purchase.products.firstOrNull() ?: "")
            }
        }
        viewModelScope.launch {
            billingManager.error.collect { errorMsg ->
                _uiState.value = PaymentUiState.Error(errorMsg)
            }
        }
    }

    private val _productDetails = MutableStateFlow<Map<String, com.android.billingclient.api.ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, com.android.billingclient.api.ProductDetails>> = _productDetails

    fun fetchProductDetails() {
        viewModelScope.launch {
            var retries = 0
            while (!billingManager.isReady() && retries < 15) {
                kotlinx.coroutines.delay(500)
                retries++
            }

            val subIds = listOf("sub_weekly_pro", "sub_monthly_pro", "sub_yearly_legend")
            val inAppIds = listOf("credits_50", "credits_200", "credits_500", "credits_pocket", "credits-pocket", "credits_starter", "credits_value", "credits_business", "credits_enterprise")

            billingManager.queryProductDetails(subIds, com.android.billingclient.api.BillingClient.ProductType.SUBS) { subsDetails ->
                billingManager.queryProductDetails(inAppIds, com.android.billingclient.api.BillingClient.ProductType.INAPP) { inAppDetails ->
                    val allDetails = (subsDetails + inAppDetails).associateBy { it.productId }
                    _productDetails.value = allDetails
                }
            }
        }
    }

    fun startPayment(activity: Activity, planId: String, isSubscription: Boolean) {
        val bundle = android.os.Bundle().apply { putString("plan_id", planId) }
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("purchase_started", bundle)
        val productType = if (isSubscription) {
            com.android.billingclient.api.BillingClient.ProductType.SUBS
        } else {
            com.android.billingclient.api.BillingClient.ProductType.INAPP
        }
        billingManager.launchPurchaseFlow(activity, planId, productType)
    }

    private fun verifyGooglePurchase(purchaseToken: String, productId: String) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Loading
            try {
                val response = RetrofitClient.apiService.verifyGooglePurchase(
                    com.jagtarapvtltd.tryzonai.network.GoogleVerifyRequest(
                        purchaseToken = purchaseToken,
                        productId = productId
                    )
                )
                
                if (response.status == "success" || response.status == "already_processed") {
                    val bundle = android.os.Bundle().apply { putString("plan_id", productId) }
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("purchase_completed", bundle)
                    _uiState.value = PaymentUiState.Success
                } else {
                    _uiState.value = PaymentUiState.Error(response.message)
                }
            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Error("Verification failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.endConnection()
    }
}

sealed class PaymentUiState {
    object Idle : PaymentUiState()
    object Loading : PaymentUiState()
    object Success : PaymentUiState()
    data class Error(val message: String) : PaymentUiState()
}
