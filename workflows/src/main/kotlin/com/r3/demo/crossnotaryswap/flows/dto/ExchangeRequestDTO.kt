package com.r3.demo.crossnotaryswap.flows.dto

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.types.AssetRequestType
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import java.math.BigDecimal
import java.util.*

@CordaSerializable
interface AbstractAssetRequest

@CordaSerializable
data class FungibleAssetRequest(val tokenAmount: Amount<TokenType>) : AbstractAssetRequest

@CordaSerializable
data class NonFungibleAssetRequest(val tokenType: TokenType, val tokenIdentifier: UUID) : AbstractAssetRequest

/**
 * Class to represent a request for an asset, not an actual asset.
 * The difference is clearly evident when talking about Fungible tokens; one can request for
 * 100$ but can be fulfilled by 5 fungible USD of 20 amount each.
 */
@CordaSerializable
open class AssetRequest(
    val tokenIdentifier: String,
    val assetRequestType: AssetRequestType,
    val amount: BigDecimal? = null
) {
    override fun toString(): String {
        return "AssetRequest(tokenIdentifier='$tokenIdentifier', assetRequestType=$assetRequestType, amount=$amount)"
    }
}

/**
 * A deal class to represent an exchange request of an NFT <> NFT, FT <> FT, or NFT <> FT
 */
@CordaSerializable
data class ExchangeRequestDTO(
    val requestId: UUID = UUID.randomUUID(),
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val buyerAssetRequest: AssetRequest,
    val sellerAssetRequest: AssetRequest,
    val requestStatus: RequestStatus? = null,
    val reason: String? = null,
    val txId: String? = null,
    val unsignedWireTransaction: WireTransaction? = null
) {
    companion object {

        fun fromExchangeRequestEntity(exchangeRequest: ExchangeRequest, serviceHub: ServiceHub): ExchangeRequestDTO =
            with(exchangeRequest) {
                ExchangeRequestDTO(
                    requestId = UUID.fromString(requestId),
                    buyer = buyer,
                    seller = seller,
                    buyerAssetRequest = AssetRequest(buyerAssetTokenIdentifier,
                        buyerAssetRequestType,
                        buyerAssetQty),
                    sellerAssetRequest = AssetRequest(sellerAssetTokenIdentifier,
                        sellerAssetRequestType,
                        sellerAssetQty),
                    requestStatus = requestStatus,
                    txId = txId,
                    unsignedWireTransaction = unsignedTransaction?.deserialize())
            }
    }

    fun toExchangeRequestEntity(): ExchangeRequest = ExchangeRequest(
        requestId = requestId.toString(),
        buyer = buyer,
        seller = seller,
        buyerAssetTokenIdentifier = buyerAssetRequest.tokenIdentifier,
        sellerAssetTokenIdentifier = sellerAssetRequest.tokenIdentifier,
        buyerAssetQty = buyerAssetRequest.amount,
        sellerAssetQty = sellerAssetRequest.amount,
        buyerAssetRequestType = buyerAssetRequest.assetRequestType,
        sellerAssetRequestType = sellerAssetRequest.assetRequestType,
        requestStatus = requestStatus,
        reason = reason,
        txId = txId,
        unsignedTransaction = unsignedWireTransaction?.serialize()?.bytes
    )

    fun approve(): ExchangeRequestDTO = this.copy(requestStatus = RequestStatus.APPROVED)
    fun deny(reason: String? = null): ExchangeRequestDTO =
        this.copy(requestStatus = RequestStatus.DENIED, reason = reason)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExchangeRequestDTO

        if (requestId != other.requestId) return false
        if (buyer != other.buyer) return false
        if (seller != other.seller) return false
        if (buyerAssetRequest != other.buyerAssetRequest) return false
        if (sellerAssetRequest != other.sellerAssetRequest) return false
        if (requestStatus != other.requestStatus) return false
        if (reason != other.reason) return false
        if (txId != other.txId) return false
        if (unsignedWireTransaction != other.unsignedWireTransaction) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + buyer.hashCode()
        result = 31 * result + seller.hashCode()
        result = 31 * result + buyerAssetRequest.hashCode()
        result = 31 * result + sellerAssetRequest.hashCode()
        result = 31 * result + (requestStatus?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        result = 31 * result + (txId?.hashCode() ?: 0)
        result = 31 * result + (unsignedWireTransaction?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ExchangeRequestDTO(" +
                "requestId=$requestId, " +
                "buyer=$buyer, " +
                "seller=$seller, " +
                "buyerAssetRequest=$buyerAssetRequest, " +
                "sellerAssetRequest=$sellerAssetRequest, " +
                "requestStatus=$requestStatus, " +
                "reason=$reason, " +
                "txId=$txId, " +
                "unsignedWireTransaction=$unsignedWireTransaction)"
    }


}