package com.r3.demo.contractdependency.states

import com.r3.demo.contractdependency.contracts.JsonStateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

@BelongsToContract(JsonStateContract::class)
data class JsonState(
    val jsonString: String,
    override val participants: List<AbstractParty>
) : ContractState