package com.r3.demo.crossnotaryswap.flows.dto

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.flows.utils.TokenRegistry
import com.r3.demo.crossnotaryswap.schemas.DBAssetRequest
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.types.AssetRequestType.FUNGIBLE_ASSET_REQUEST
import com.r3.demo.crossnotaryswap.types.AssetRequestType.NON_FUNGIBLE_ASSET_REQUEST
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import java.util.*

@CordaSerializable
interface AbstractAssetRequest {
    fun toDBRepresentableForm(): DBAssetRequest
}

@CordaSerializable
data class FungibleAssetRequest(val tokenAmount: Amount<TokenType>) : AbstractAssetRequest {
    override fun toDBRepresentableForm(): DBAssetRequest =
        DBAssetRequest(tokenAmount.token.tokenIdentifier, FUNGIBLE_ASSET_REQUEST, tokenAmount.quantity)

}

@CordaSerializable
data class NonFungibleAssetRequest(val tokenIdentifier: String) : AbstractAssetRequest {
    override fun toDBRepresentableForm(): DBAssetRequest =
        DBAssetRequest(tokenIdentifier, NON_FUNGIBLE_ASSET_REQUEST)
}


fun createAssetRequestFromDBAsset(dbAssetRequest: DBAssetRequest): AbstractAssetRequest {
    return with(dbAssetRequest) {
        when (assetRequestType) {
            FUNGIBLE_ASSET_REQUEST -> {
                require(amount != null) { "Amount cannot be null if token is of FungibleToken type" }
                FungibleAssetRequest(Amount(amount!!, TokenRegistry.getInstance(tokenIdentifier)))
            }
            NON_FUNGIBLE_ASSET_REQUEST -> {
                NonFungibleAssetRequest(tokenIdentifier)
            }
        }
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
    val buyerAssetRequest: AbstractAssetRequest,
    val sellerAssetRequest: AbstractAssetRequest,
    val requestStatus: RequestStatus? = null,
    val reason: String? = null,
    val txId: String? = null,
    val unsignedWireTransaction: WireTransaction? = null
) {
    companion object {

        fun fromExchangeRequestEntity(exchangeRequest: ExchangeRequest): ExchangeRequestDTO =
            with(exchangeRequest) {
                ExchangeRequestDTO(
                    requestId = UUID.fromString(requestId),
                    buyer = buyer,
                    seller = seller,
                    buyerAssetRequest = createAssetRequestFromDBAsset(buyerAssetRequest),
                    sellerAssetRequest = createAssetRequestFromDBAsset(sellerAssetRequest),
                    requestStatus = requestStatus,
                    txId = txId,
                    unsignedWireTransaction = unsignedTransaction?.deserialize())
            }
    }

    fun toExchangeRequestEntity(): ExchangeRequest = ExchangeRequest(
        requestId = requestId.toString(),
        buyer = buyer,
        seller = seller,
        buyerAssetRequest = buyerAssetRequest.toDBRepresentableForm(),
        sellerAssetRequest = sellerAssetRequest.toDBRepresentableForm(),
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