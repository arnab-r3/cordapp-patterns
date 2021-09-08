package com.r3.demo.stateencapsulation.contracts

import com.template.states.EncapsulatedState
import com.template.states.EncapsulatingState
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

                    "Create Command should not have any inputs" using
                            (inputEncapsulatedStates.isEmpty() && inputEncapsulatedStates.isEmpty())

                    "Create Command should have only one output" using
                            (outputEncapsulatingStates.size == 1 && outputEncapsulatedStates.isEmpty())

                }

            is Commands.CreateEnclosed ->
                requireThat {
                    "Create Command should not have any inputs" using
                            (inputEncapsulatedStates.isEmpty() && inputEncapsulatedStates.isEmpty())

                    "Create Command should have only one output" using
                            (outputEncapsulatingStates.isEmpty() && outputEncapsulatedStates.size == 1)

                }
            is Commands.UpdateEncapsulating ->


                requireThat {

                    // ensure that we are not spending or creating any encapsulated state
                    "Update should not contain any encapsulated state as input or encapsulating state as out" using
                            (inputEncapsulatedStates.isEmpty() && outputEncapsulatedStates.isEmpty())


                    // ensure that we are spending exactly one encapsulating state and creating an encapsulating state
                    "Update command should have one input" using (inputEncapsulatingStates.size == 1)
                    "Update command should have one output" using (outputEncapsulatingStates.size == 1)

                    //ensure that the created encapsulating state is the updated one and we are not creating any new ones
                    "Update should produce the same encapsulating state" using
                            (inputEncapsulatingStates.single().linearId == outputEncapsulatingStates.single().linearId)

                    "Update should use the same encapsulated state" using
                            (inputEncapsulatingStates.single().encapsulatedStateIdentifier == outputEncapsulatingStates.single().encapsulatedStateIdentifier)


                }
            is Commands.UpdateEnclosed ->
                requireThat {
                    "Updating the enclosed transaction should not contain the enclosing transaction as input or output" using
                            (outputEncapsulatingStates.isEmpty() && inputEncapsulatingStates.isEmpty())
                    "Updating the enclosed state should have one input and one output" using
                            (inputEncapsulatedStates.size == 1 && outputEncapsulatedStates.size == 1)

                    "Updating the encapsulated state should use the same identifier as the input and output" using
                            (inputEncapsulatedStates.single().linearId == outputEncapsulatedStates.single().linearId)

                }
        }
    }

    interface Commands : CommandData {
        class CreateEncapsulating : Commands
        class UpdateEncapsulating : Commands
        class UpdateEnclosed : Commands
        class CreateEnclosed : Commands


    }

}