package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class GroupDataAssociationContract: Contract {

    companion object {
        const val ID = "com.r3.demo.datadistribution.contracts.GroupDataAssociationContract"
    }

    override fun verify(tx: LedgerTransaction)  {

        val command = tx.commands.requireSingleCommand<Commands>()
        val groupDataAssociationStateInputs = tx.inputsOfType<GroupDataAssociationState>()
        val groupDataAssociationStateOutputs = tx.outputsOfType<GroupDataAssociationState>()

        when (command.value) {
            is Commands.CreateData -> requireThat {
                "CreateData command should not have any input states" using tx.inputStates.isEmpty()
                "CreateData command should have a single output state of type GroupDataAssociationState" using
                        (groupDataAssociationStateOutputs.size == 1)
                "CreateData command should have mandatory value & participant field" using
                        (groupDataAssociationStateOutputs.single().value != null
                                && groupDataAssociationStateOutputs.single().participants.isNotEmpty())
            }
            is Commands.UpdateGroups -> {
                requireThat {
                    "UpdateGroups command should have a single input state" using (groupDataAssociationStateInputs.size == 1)
                    "UpdateGroups command should have a single output state" using (groupDataAssociationStateOutputs.size == 1)
                    "UpdateGroups command should have same data identifier" using
                            (groupDataAssociationStateOutputs.single().linearId == groupDataAssociationStateInputs.single().linearId)
                    "UpdateGroups should not change the value" using
                            (groupDataAssociationStateOutputs.single().value == groupDataAssociationStateInputs.single().value)
                }
            }

        }

    }

    interface Commands: CommandData {
        class CreateData : TypeOnlyCommandData(), Commands
        class UpdateGroups: TypeOnlyCommandData(), Commands
    }
}