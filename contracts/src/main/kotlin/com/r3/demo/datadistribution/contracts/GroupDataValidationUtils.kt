package com.r3.demo.datadistribution.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

/**
 * @param tx - the ledger transaction
 * @param referredGroupDataAssociationPointer - the state that refers to the GroupDataAssociationState for validation
 */
fun validateAndFetchGroupParticipants(
    tx: LedgerTransaction,
    referredGroupDataAssociationPointer: LinearPointer<GroupDataAssociationState>
): Set<Party> {
    val grpDataAssociationStates = tx.referenceInputRefsOfType<GroupDataAssociationState>()
    val groupStatesReferred = tx.referenceInputRefsOfType<GroupState>()
    val outputIoUState = tx.outputsOfType<IOUState>().single()

    requireThat {
        //check presence of the reference state
        "The transaction should contain exactly one reference state of type GroupDataAssociationState" using (grpDataAssociationStates.size == 1)

        val grpDataAssociationState = grpDataAssociationStates.single().state.data

        "The transaction should include the reference to the same GroupDataReferenceState as mentioned in IoUState" using
                (grpDataAssociationState.linearId == referredGroupDataAssociationPointer.pointer)

        val groupIdsReferred = grpDataAssociationState.associatedGroupStates?.map {
            it.pointer
        } ?: throw IllegalArgumentException("Groups in GroupDataAssociationState should not be empty")


        "Transaction should include every instance of group state that is referred in GroupDataAssociationState" using
                (groupStatesReferred.map { it.state.data.linearId }.containsAll(groupIdsReferred))

        return groupStatesReferred.filter { it.state.data.linearId in groupIdsReferred }
            .flatMap { it.state.data.participants }.toSet()

    }

}