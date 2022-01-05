package com.r3.demo.crossnotaryswap.flows.dto

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.demo.crossnotaryswap.flows.utils.TokenRegistry
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import java.util.*

//data class Asset(val assetId: UUID)
/**
 * A deal class to represent DvP or PvP request
 */
@CordaSerializable
data class ExchangeRequestDTO (
    val requestId: UUID = UUID.randomUUID(),
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val buyerAsset: Amount<TokenType>,
    val sellerAsset: Amount<TokenType>,
    val requestStatus: RequestStatus? = null,
    val txId: String? = null
) {
    companion object {
        fun fromExchangeRequestEntity(exchangeRequest: ExchangeRequest): ExchangeRequestDTO = ExchangeRequestDTO(
            requestId = UUID.fromString(exchangeRequest.requestId),
            buyer = exchangeRequest.buyer,
            seller = exchangeRequest.seller,
            buyerAsset = exchangeRequest.buyerAssetQty of TokenRegistry.getInstance(exchangeRequest.buyerAssetType),
            sellerAsset = exchangeRequest.sellerAssetQty of TokenRegistry.getInstance(exchangeRequest.sellerAssetType),
            requestStatus = exchangeRequest.requestStatus,
            txId = exchangeRequest.txId
        )
    }

    fun toExchangeRequestEntity() : ExchangeRequest = ExchangeRequest(
        requestId = requestId.toString(),
        buyer = buyer,
        seller = seller,
        buyerAssetType = buyerAsset.token.tokenIdentifier,
        sellerAssetType = sellerAsset.token.tokenIdentifier,
        buyerAssetQty = buyerAsset.quantity,
        sellerAssetQty = sellerAsset.quantity,
        requestStatus = requestStatus
    )

    fun approve(): ExchangeRequestDTO = this.copy(requestStatus = RequestStatus.APPROVED)
    fun reject(): ExchangeRequestDTO = this.copy(requestStatus = RequestStatus.REQUESTED)

}