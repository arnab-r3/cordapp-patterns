package com.r3.demo.datadistribution.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState


@BelongsToContract(GroupDataAssociationContract::class)
data class GroupDataAssociationState(
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    val value: Any?,
    val associatedGroupStates: Set<LinearPointer<GroupState>>?,
    override val participants: List<AbstractParty>
): LinearState


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