package com.goldcalculateur.app.core

enum class PurchaseStateKind { PURCHASED, PENDING, UNSPECIFIED }

/** SDK-independent view of a Google Play purchase. */
data class PurchaseSnapshot(
    val productIds: List<String>,
    val state: PurchaseStateKind,
    val acknowledged: Boolean,
    val token: String,
)

data class EntitlementDecision(
    val entitled: Boolean,
    val pending: Boolean,
    /** Purchase tokens that are PURCHASED but still need acknowledgePurchase(). */
    val tokensToAcknowledge: List<String>,
)

enum class BillingFailure { NONE, CANCELLED, ALREADY_OWNED, NETWORK, UNAVAILABLE, OTHER }

object EntitlementLogic {

    /** The purchase is only a success when Google Play reports state PURCHASED for our product. */
    fun decide(productId: String, purchases: List<PurchaseSnapshot>): EntitlementDecision {
        val mine = purchases.filter { productId in it.productIds }
        val purchased = mine.filter { it.state == PurchaseStateKind.PURCHASED }
        return EntitlementDecision(
            entitled = purchased.isNotEmpty(),
            pending = mine.any { it.state == PurchaseStateKind.PENDING },
            tokensToAcknowledge = purchased.filter { !it.acknowledged }.map { it.token },
        )
    }

    /** Maps BillingClient.BillingResponseCode values. */
    fun classify(responseCode: Int): BillingFailure = when (responseCode) {
        0 -> BillingFailure.NONE
        1 -> BillingFailure.CANCELLED
        7 -> BillingFailure.ALREADY_OWNED
        2, 12, -1, -3 -> BillingFailure.NETWORK // SERVICE_UNAVAILABLE, NETWORK_ERROR, SERVICE_DISCONNECTED, TIMEOUT
        3, 4, -2 -> BillingFailure.UNAVAILABLE // BILLING_UNAVAILABLE, ITEM_UNAVAILABLE, FEATURE_NOT_SUPPORTED
        else -> BillingFailure.OTHER
    }
}
