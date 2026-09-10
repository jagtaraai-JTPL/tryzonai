package com.jagtarapvtltd.tryzonai.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(private val context: Context, private val scope: CoroutineScope) {

    private val _purchaseSuccess = MutableSharedFlow<Purchase>()
    val purchaseSuccess: SharedFlow<Purchase> = _purchaseSuccess

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                scope.launch { handlePurchase(purchase) }
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
            // User canceled the purchase
        } else {
            scope.launch { _error.emit("Billing Error: ${billingResult.debugMessage}") }
        }
    }

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    // Query for existing unacknowledged subscriptions
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                    ) { result, purchases ->
                        if (result.responseCode == BillingResponseCode.OK) {
                            for (purchase in purchases) {
                                scope.launch { handlePurchase(purchase) }
                            }
                        }
                    }
                    
                    // Query for existing unconsumed in-app products
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                    ) { result, purchases ->
                        if (result.responseCode == BillingResponseCode.OK) {
                            for (purchase in purchases) {
                                scope.launch { handlePurchase(purchase) }
                            }
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                startConnection()
            }
        })
    }

    fun launchPurchaseFlow(activity: Activity, productId: String, productType: String) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList
            if (billingResult.responseCode == BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                val productDetails = productDetailsList[0]
                
                val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                
                if (productType == com.android.billingclient.api.BillingClient.ProductType.SUBS) {
                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offerToken != null) {
                        productDetailsParamsBuilder.setOfferToken(offerToken)
                    } else {
                        scope.launch { _error.emit("No active offer found for subscription: $productId") }
                        return@queryProductDetailsAsync
                    }
                }

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                scope.launch { _error.emit("Product not found: $productId") }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val isConsumable = purchase.products.any { it.startsWith("credits_") || it.startsWith("credits-") }
            
            if (isConsumable) {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val consumeResult = withContext(Dispatchers.IO) {
                    billingClient.consumePurchase(consumeParams)
                }
                if (consumeResult.billingResult.responseCode == BillingResponseCode.OK) {
                    _purchaseSuccess.emit(purchase)
                } else {
                    _error.emit("Failed to consume purchase")
                }
            } else {
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val ackResult = withContext(Dispatchers.IO) {
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams)
                    }
                    if (ackResult.responseCode == BillingResponseCode.OK) {
                        _purchaseSuccess.emit(purchase)
                    } else {
                        _error.emit("Failed to acknowledge subscription")
                    }
                } else {
                    _purchaseSuccess.emit(purchase)
                }
            }
        }
    }
    
    fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        onResult: (List<ProductDetails>) -> Unit
    ) {
        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                onResult(queryProductDetailsResult.productDetailsList)
            } else {
                onResult(emptyList())
            }
        }
    }
    
    fun endConnection() {
        billingClient.endConnection()
    }
    
    fun isReady(): Boolean {
        return billingClient.isReady
    }
}
