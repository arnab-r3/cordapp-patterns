package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param value the value of the IOU.
 * @param lender the party issuing the IOU.
 * @param groupDataAssociationState the group data state that defines how much the IoU state should lend
 * @param borrower the party receiving and approving the IOU.
 */
@BelongsToContract(IOUContract::class)
data class IOUState(val value: Int,
                    val lender: Party,
                    val borrower: Party,
                    val groupDataAssociationState: LinearPointer<GroupDataAssociationState>,
                    override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

}
