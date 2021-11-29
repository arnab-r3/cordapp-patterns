package com.r3.demo.datadistribution.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.*
import net.corda.core.identity.Party


@BelongsToContract(GroupDataAssociationContract::class)
data class GroupDataAssociationState(
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    val metaData: Map<String, String>,
    //val data: Any,// be careful, Any --> Object in Java, All subclasses of Object type cannot be @CordaSerializable
    val data: Set<StatePointer<out ContractState>>, // link the other states to this state
    val associatedGroupStates: Set<LinearPointer<GroupState>>,
    override val participants: List<Party>
): LinearState
