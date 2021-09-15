package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

data class ConfigurationState(
    val value: String,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        TODO("Not yet implemented")
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        TODO("Not yet implemented")
    }
}