package com.r3.demo.crossnotaryswap.flows.dto

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.flows.utils.TokenRegistry
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.types.RequestStatus
import com.r3.demo.generic.argFail
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import java.util.*

/**
 * Class to represent a buyer or seller asset
 */
@CordaSerializable
open class ExchangeAsset<T : TokenType>(
    val tokenType: T,
    val amount: Amount<T>? = null
) {

    companion object {
        fun toAssetType(
            tokenIdentifier: String,
            amount: Long? = null,
            tokenClass: Class<out EvolvableTokenType>?,
            serviceHub: ServiceHub
        ): ExchangeAsset<out TokenType> {
            val tokenType = TokenRegistry.getInstance(tokenIdentifier, serviceHub, tokenClass)
            return amount?.let {
                ExchangeAsset(tokenType = tokenType, amount = Amount(amount, tokenType))
            } ?: ExchangeAsset(tokenType = tokenType)
        }
    }

    fun toTokenIdentifierAndAmount(): Pair<String, Long?> {
        val tokenIdentifier = with(tokenType) {
            when {
                isRegularTokenType() -> tokenIdentifier
                isPointer() -> TokenRegistry.getTokenAbbreviation(tokenClass)
                else -> argFail("Unable to determine tokenType. Should be either token pointer or regular token")
            }
        }
        return if (amount != null) {
            tokenIdentifier to amount.quantity
        } else tokenIdentifier to null
    }

    override fun toString(): String {
        return "ExchangeAsset(tokenType=$tokenType, amount=$amount)"
    }
}

//data class Asset(val assetId: UUID)
/**
 * A deal class to represent DvP or PvP request
 */
@CordaSerializable
data class ExchangeRequestDTO(
    val requestId: UUID = UUID.randomUUID(),
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val buyerAsset: ExchangeAsset<out TokenType>,
    val sellerAsset: ExchangeAsset<out TokenType>,
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
                    buyerAsset = ExchangeAsset.toAssetType(
                        buyerAssetType,
                        buyerAssetQty,
                        buyerAssetClass,
                        serviceHub),
                    sellerAsset = ExchangeAsset.toAssetType(
                        buyerAssetType,
                        sellerAssetQty,
                        sellerAssetClass,
                        serviceHub),
                    requestStatus = requestStatus,
                    txId = txId,
                    unsignedWireTransaction = unsignedTransaction?.deserialize())
            }
    }

    fun toExchangeRequestEntity(): ExchangeRequest = ExchangeRequest(
        requestId = requestId.toString(),
        buyer = buyer,
        seller = seller,
        buyerAssetType = buyerAsset.tokenType.tokenIdentifier,
        sellerAssetType = sellerAsset.tokenType.tokenIdentifier,
        buyerAssetQty = buyerAsset.amount?.quantity,
        sellerAssetQty = sellerAsset.amount?.quantity,
        requestStatus = requestStatus,
        buyerAssetClass = uncheckedCast(buyerAsset.tokenType.tokenClass),
        sellerAssetClass = uncheckedCast(sellerAsset.tokenType.tokenClass),
        reason = reason,
        unsignedTransaction = unsignedWireTransaction?.serialize()?.bytes
    )

    fun approve(): ExchangeRequestDTO = this.copy(requestStatus = RequestStatus.APPROVED)
    fun reject(reason: String? = null): ExchangeRequestDTO =
        this.copy(requestStatus = RequestStatus.REQUESTED, reason = reason)


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExchangeRequestDTO
        if (requestId != other.requestId) return false
        if (buyer != other.buyer) return false
        if (seller != other.seller) return false
        if (buyerAsset != other.buyerAsset) return false
        if (sellerAsset != other.sellerAsset) return false
        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + buyer.hashCode()
        result = 31 * result + seller.hashCode()
        result = 31 * result + buyerAsset.hashCode()
        result = 31 * result + sellerAsset.hashCode()
        result = 31 * result + (requestStatus?.hashCode() ?: 0)
        result = 31 * result + (txId?.hashCode() ?: 0)
        return result
    }


}