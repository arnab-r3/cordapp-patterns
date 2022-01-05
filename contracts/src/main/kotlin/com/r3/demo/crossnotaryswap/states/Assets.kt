package com.r3.demo.crossnotaryswap.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.demo.crossnotaryswap.contracts.KittyTokenContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(KittyTokenContract::class)
data class KittyToken(
    val kittyName: String,
    override val fractionDigits: Int,
    override val maintainers: List<Party>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : EvolvableTokenType()

