package com.r3.demo.crossnotaryswap.forms

import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.demo.crossnotaryswap.flows.dto.AbstractAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.NFTTokenType
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import net.corda.core.identity.Party
import java.math.BigDecimal

/**
 * Enclosing Class to represent cross notary swap data objects from REST
 */
class CNSForms {

    class AssetRequestForm(
        val tokenIdentifier: String = "",
        val amount: BigDecimal? = BigDecimal.ZERO,
        val receiver: Party
    ){
        fun toAssetRequest(): AbstractAssetRequest {
            return when (amount) {
                null -> NonFungibleAssetRequest(tokenIdentifier)
                else -> FungibleAssetRequest(amount of FiatCurrency.getInstance(tokenIdentifier))
            }
        }

        override fun toString(): String {
            return "tokenIdentifier='$tokenIdentifier', amount=$amount, receiver=$receiver"
        }
    }

    class SellerApprovalForm(
        val requestId: String = "",
        val approved: Boolean? = false,
        val rejectionReason: String? = ""
    )

    class BuyerAssetRequestForm(
        val seller: Party,
        val buyerAsset: AssetRequestForm,
        val sellerAsset: AssetRequestForm
    ){
        override fun toString(): String {
            return "seller=$seller, \nbuyerAsset=$buyerAsset, \nsellerAsset=$sellerAsset"
        }
    }

    class NFTDefinition {
        val properties: Map<String, String> = mapOf()
        val type: NFTTokenType = NFTTokenType.KITTY
        val maintainers = listOf<String>()
    }
}