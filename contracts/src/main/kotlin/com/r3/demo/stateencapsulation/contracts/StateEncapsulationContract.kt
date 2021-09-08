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
                    "${command.value} Command should not have any inputs" using tx.inputStates.isEmpty()
                    "${command.value} Command should not produce encapsulated states" using inputEncapsulatedStates.isEmpty()
                    "${command.value} Command should produce a single encapsulating state" using (inputEncapsulatingStates.size == 1)
                }

            is Commands.CreateEncapsulated ->
                requireThat {
                    "${command.value} Command should not have any inputs" using tx.inputStates.isEmpty()
                    "${command.value} Command should not produce encapsulating states" using (inputEncapsulatingStates.isEmpty())
                    "${command.value} Command should not produce a single encapsulated state" using (inputEncapsulatedStates.size == 1)
                }

            is Commands.UpdateEncapsulating ->
                requireThat {
                    "${command.value} Command should not produce any encapsulated state" using outputEncapsulatedStates.isEmpty()
                    "${command.value} Command should not consume any encapsulated state" using inputEncapsulatedStates.isEmpty()
                    "${command.value} Command should produce exactly one encapsulating state" using (outputEncapsulatingStates.size == 1)
                    "${command.value} Command should consume exactly one encapsulating state" using (inputEncapsulatingStates.size == 1)
                    "${command.value} Command should use the same encapsulated state identifier" using
                            (inputEncapsulatingStates.single().encapsulatedStateIdentifier
                                    == outputEncapsulatingStates.single().encapsulatedStateIdentifier)
                    "${command.value} Command should update the same encapsulating state" using
                            (inputEncapsulatingStates.single().linearId
                                    == outputEncapsulatingStates.single().linearId)
                }

            is Commands.UpdateEncapsulated ->
                requireThat {
                    "${command.value} Command should not produce any encapsulating state" using outputEncapsulatedStates.isEmpty()
                    "${command.value} Command should not consume any encapsulating state" using inputEncapsulatingStates.isEmpty()
                    "${command.value} Command should produce exactly one encapsulated state" using (outputEncapsulatedStates.size == 1)
                    "${command.value} Command should consume exactly one encapsulated state" using (inputEncapsulatedStates.size == 1)
                    "${command.value} Command should update the same encapsulating state" using
                            (inputEncapsulatedStates.single().linearId
                                    == outputEncapsulatedStates.single().linearId)
                }
        }
    }

    interface Commands : CommandData {
        class CreateEncapsulating : Commands
        class UpdateEncapsulating : Commands
        class UpdateEncapsulated : Commands
        class CreateEncapsulated : Commands


    }

}