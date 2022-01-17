package com.r3.demo.crossnotaryswap.forms

import com.r3.demo.crossnotaryswap.flows.dto.NFTTokenType
import com.r3.demo.crossnotaryswap.types.AssetRequestType
import net.corda.core.identity.CordaX500Name

/**
 * Enclosing Class to represent cross notary swap data objects from REST
 */
class CNSForms {

    class AssetRequest(
        val tokenIdentifier: String = "",
        val assetRequestType: AssetRequestType = AssetRequestType.FUNGIBLE_ASSET_REQUEST,
        val amount: Long? = 0L
    )

    class NFTDefinition {
        val properties: Map<String, String> = mapOf()
        val type: NFTTokenType = NFTTokenType.KITTY
        val maintainers = listOf<CordaX500Name>()
    }
}