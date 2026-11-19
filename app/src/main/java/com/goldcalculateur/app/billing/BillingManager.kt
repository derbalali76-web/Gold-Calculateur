package com.goldcalculateur.app.billing

import android.app.Activity
import android.app.Application
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.goldcalculateur.app.BuildConfig
import com.goldcalculateur.app.core.BillingFailure
import com.goldcalculateur.app.core.EntitlementLogic
import com.goldcalculateur.app.core.PurchaseSnapshot
import com.goldcalculateur.app.core.PurchaseStateKind
import com.goldcalculateur.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BillingStatus { IDLE, PURCHASING, PENDING, SUCCESS, CANCELLED, NETWORK, UNAVAILABLE, ERROR, RESTORED }

data class BillingUiState(
    /** Localised price straight from Google Play (e.g. "1,99 US$"), null until loaded. */
    val priceText: String? = null,
    val status: BillingStatus = BillingStatus.IDLE,
)

/**
 * Google Play Billing for the single non-consumable product `remove_ads_lifetime`.
 *
 * Rules implemented:
 *  - the purchase counts ONLY when Google Play reports state PURCHASED (never on a button press);
 *  - PENDING purchases do not remove ads;
 *  - PURCHASED + not acknowledged -> acknowledgePurchase() (otherwise Google refunds after 3 days);
 *  - entitlement is restored from queryPurchasesAsync() on every connection / app resume,
 *    which also covers re-installs on the same Google account;
 *  - the entitlement is cached locally so the app starts ad-free offline; a SUCCESSFUL query that
 *    returns no purchase (e.g. refund) removes the cache; a failed query changes nothing.
 *
 * Limitation: this is on-device verification. Play's own signed purchase state is trusted by the
 * app; for stronger anti-fraud, verify purchase tokens on a server with the Google Play Developer
 * API. See docs/PUBLISHING.md.
 */
class BillingManager(
    app: Application,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) : PurchasesUpdatedListener {

    private val productId = BuildConfig.REMOVE_ADS_PRODUCT_ID
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state

    private var productDetails: ProductDetails? = null
    private var reconnectAttempts = 0

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() = connect()

    private fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    scope.launch {
                        loadProductDetails()
                        refreshPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode}")
                    fail(EntitlementLogic.classify(result.responseCode))
                    scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() = scheduleReconnect()
        })
    }

    private fun scheduleReconnect() {
        reconnectAttempts++
        if (reconnectAttempts > MAX_RECONNECTS) return
        scope.launch {
            delay(minOf(2_000L * reconnectAttempts, 30_000L))
            connect()
        }
    }

    private suspend fun loadProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetails = result.productDetailsList?.firstOrNull()
            val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update { it.copy(priceText = price) }
        }
    }

    /** Restores the entitlement (also called when the app resumes and from "Restore purchases"). */
    fun refreshPurchases(userInitiated: Boolean = false) {
        scope.launch { queryAndApply(userInitiated) }
    }

    private suspend fun queryAndApply(userInitiated: Boolean) {
        if (!client.isReady) {
            connect()
            if (userInitiated) fail(BillingFailure.NETWORK)
            return
        }
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            if (userInitiated) fail(EntitlementLogic.classify(result.billingResult.responseCode))
            return // keep the cached entitlement untouched when the check itself failed
        }
        apply(result.purchasesList, authoritative = true)
        if (userInitiated && _state.value.status == BillingStatus.IDLE) {
            _state.update { it.copy(status = BillingStatus.RESTORED) }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (!client.isReady || details == null) {
            fail(if (client.isReady) BillingFailure.UNAVAILABLE else BillingFailure.NETWORK)
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            ).build()
        _state.update { it.copy(status = BillingStatus.PURCHASING) }
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            fail(EntitlementLogic.classify(result.responseCode))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (val code = result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { apply(purchases.orEmpty(), authoritative = false) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> fail(EntitlementLogic.classify(code))
        }
    }

    private suspend fun apply(purchases: List<Purchase>, authoritative: Boolean) {
        val snapshots = purchases.map { it.toSnapshot() }
        val decision = EntitlementLogic.decide(productId, snapshots)

        decision.tokensToAcknowledge.forEach { token ->
            val ack = client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
            )
            // A failed acknowledgement is retried on the next refreshPurchases() (still inside Google's 3 days).
            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${ack.responseCode}")
            }
        }

        when {
            decision.entitled -> {
                settings.setAdsRemoved(true)
                _state.update { it.copy(status = BillingStatus.SUCCESS) }
            }
            decision.pending -> _state.update { it.copy(status = BillingStatus.PENDING) }
            authoritative -> settings.setAdsRemoved(false) // e.g. refunded
        }
    }

    private fun Purchase.toSnapshot() = PurchaseSnapshot(
        productIds = products,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseStateKind.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseStateKind.PENDING
            else -> PurchaseStateKind.UNSPECIFIED
        },
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )

    private fun fail(kind: BillingFailure) {
        val status = when (kind) {
            BillingFailure.NONE -> return
            BillingFailure.CANCELLED -> BillingStatus.CANCELLED
            BillingFailure.ALREADY_OWNED -> BillingStatus.RESTORED
            BillingFailure.NETWORK -> BillingStatus.NETWORK
            BillingFailure.UNAVAILABLE -> BillingStatus.UNAVAILABLE
            BillingFailure.OTHER -> BillingStatus.ERROR
        }
        _state.update { it.copy(status = status) }
    }

    fun consumeStatus() = _state.update { it.copy(status = BillingStatus.IDLE) }

    private companion object {
        const val TAG = "BillingManager"
        const val MAX_RECONNECTS = 5
    }
}
