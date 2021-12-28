package com.r3.crossnotaryswap

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class KittyToken(
    val kittyName: String,
    override val fractionDigits: Int,
    override val maintainers: List<Party>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : EvolvableTokenType()



