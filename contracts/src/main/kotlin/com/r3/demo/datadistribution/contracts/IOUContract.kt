package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOUState].
 *
 * For a new [IOUState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOUState].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.r3.demo.datadistribution.contracts.IOUContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Create>()
        val grpDataAssociationStates = tx.referenceInputRefsOfType<GroupDataAssociationState>()
        val outputIoUState = tx.outputsOfType<IOUState>().single()

        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val outputState = tx.outputsOfType<IOUState>().single()
            "The lender and the borrower cannot be the same entity." using (outputState.lender != outputState.borrower)
            "All of the participants must be signers." using (command.signers.containsAll(outputState.participants.map { it.owningKey }))

            // IOU-specific constraints.
            "The IOU's value must be non-negative." using (outputState.value > 0)


            // Group data specific validations
            val groupParties = validateAndFetchGroupParticipants(tx, outputIoUState.groupDataAssociationState)

            val grpDataAssociationState = grpDataAssociationStates.single().state.data

            "Borrower and lender should be a part of the referred groups in GroupDataAssociationState" using
                    (outputIoUState.borrower in groupParties && outputIoUState.lender in groupParties)

            // assume that the GroupData association state contains the value within the limits defined in the group data association state


            "GroupDataAssociationState should contain the max value that can be lent" using
                    (grpDataAssociationState.metaData.containsKey("iouLimit"))

            val maxValue: Int
            try {
                maxValue = grpDataAssociationState.metaData["iouLimit"]?.toInt()
                    ?: throw IllegalArgumentException("metadata should contain max iou limit")
            } catch (e: ClassCastException) {
                throw IllegalArgumentException("")
            }

            "The IoU cannot have a value more than the configured maxIouValue in GroupDataAssociationState" using
                    (outputIoUState.value <= maxValue)

        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
    }
}
