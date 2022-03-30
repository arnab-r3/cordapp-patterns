package com.r3.demo.stateencapsulation.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class StateEncapsulationContract : Contract {

    companion object {
        const val ID = "com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val inputEncapsulatingStates = tx.inputsOfType<EncapsulatingState>()
        val inputEncapsulatedStates = tx.inputsOfType<EncapsulatedState>()
        val outputEncapsulatingStates = tx.outputsOfType<EncapsulatingState>()
        val outputEncapsulatedStates = tx.outputsOfType<EncapsulatedState>()

        // ensure there is a single command and is used from the Command class inside the contract.
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {

            is Commands.CreateEncapsulating ->
                requireThat {
                    "CreateEncapsulating Command should not have any inputs" using tx.inputStates.isEmpty()
                    "CreateEncapsulating Command should not produce encapsulated states" using outputEncapsulatedStates.isEmpty()
                    "CreateEncapsulating Command should produce a single encapsulating state" using (outputEncapsulatingStates.size == 1)
                }

            is Commands.CreateEncapsulated ->
                requireThat {
                    "CreateEncapsulated Command should not have any inputs" using tx.inputStates.isEmpty()
                    "CreateEncapsulated Command should not produce encapsulating states" using outputEncapsulatingStates.isEmpty()
                    "CreateEncapsulated Command should produce a single encapsulated state" using (outputEncapsulatedStates.size == 1)
                }

            is Commands.UpdateEncapsulating ->
                requireThat {
                    "UpdateEncapsulating Command should not produce any encapsulated state" using outputEncapsulatedStates.isEmpty()
                    "UpdateEncapsulating Command should not consume any encapsulated state" using inputEncapsulatedStates.isEmpty()
                    "UpdateEncapsulating Command should produce exactly one encapsulating state" using (outputEncapsulatingStates.size == 1)
                    "UpdateEncapsulating Command should consume exactly one encapsulating state" using (inputEncapsulatingStates.size == 1)
                    "UpdateEncapsulating Command should use the same encapsulated state identifier" using
                            (inputEncapsulatingStates.single().encapsulatedStateIdentifier
                                    == outputEncapsulatingStates.single().encapsulatedStateIdentifier)
                    "UpdateEncapsulating Command should update the same encapsulating state" using
                            (inputEncapsulatingStates.single().linearId
                                    == outputEncapsulatingStates.single().linearId)
                    "UpdateEncapsulating Command should not modify the list of participants" using
                            (inputEncapsulatingStates.single().participants.containsAll(outputEncapsulatingStates.single().participants))
                    "UpdateEncapsulating Command should collect the signatures of all participants for modification" using
                            (command.signers.containsAll(inputEncapsulatingStates.single().participants.map { it.owningKey }))
                }

            is Commands.UpdateEncapsulated ->
                requireThat {
                    "UpdateEncapsulated Command should not produce any encapsulating state" using outputEncapsulatingStates.isEmpty()
                    "UpdateEncapsulated Command should not consume any encapsulating state" using inputEncapsulatingStates.isEmpty()
                    "UpdateEncapsulated Command should produce exactly one encapsulated state" using (outputEncapsulatedStates.size == 1)
                    "UpdateEncapsulated Command should consume exactly one encapsulated state" using (inputEncapsulatedStates.size == 1)
                    "UpdateEncapsulated Command should update the same encapsulating state" using
                            (inputEncapsulatedStates.single().linearId
                                    == outputEncapsulatedStates.single().linearId)
                    "UpdateEncapsulated Command should not modify the list of participants" using
                            (inputEncapsulatedStates.single().participants.containsAll(outputEncapsulatedStates.single().participants))
                    "UpdateEncapsulated Command should collect the signatures of all participants for modification" using
                            (command.signers.containsAll(inputEncapsulatedStates.single().participants.map { it.owningKey }))
                }
            is Commands.TestPrivacyContract -> {
            }
        }
    }

    interface Commands : CommandData {
        class CreateEncapsulating : Commands
        class UpdateEncapsulating : Commands
        class UpdateEncapsulated : Commands
        class CreateEncapsulated : Commands
        class TestPrivacyContract : Commands


    }

}