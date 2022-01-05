package com.r3.demo.crossnotaryswap.flows.dto

import com.r3.demo.crossnotaryswap.states.KittyToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub

enum class NFTTokenType {
    KITTY
}

abstract class TokenDefinition() {
    abstract val nftTokenType: NFTTokenType
}

data class KittyTokenDefinition(
    val kittyName: String,
    val maintainers: List<String>
) : TokenDefinition() {

    override val nftTokenType: NFTTokenType
        get() = NFTTokenType.KITTY

    fun toKittyToken(serviceHub: ServiceHub): KittyToken {
        val maintainerParties =
            maintainers.map { serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(it))!! }
        return KittyToken(kittyName, 0, maintainerParties)
    }
}