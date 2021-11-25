package com.r3.demo.datadistribution.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party


@BelongsToContract(GroupDataAssociationContract::class)
data class GroupDataAssociationState(
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    val metaData: Map<String, String>,
    val associatedGroupStates: Set<LinearPointer<GroupState>>,
    override val participants: List<Party>
): LinearState
