package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class GroupDataAssociationContract : Contract {

    companion object {
        const val ID = "com.r3.demo.datadistribution.contracts.GroupDataAssociationContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val groupDataAssociationStateInputs = tx.inputsOfType<GroupDataAssociationState>()
        val groupDataAssociationStateOutputs = tx.outputsOfType<GroupDataAssociationState>()

        when (command.value) {
            is Commands.CreateData -> requireThat {
                "CreateData command should not have any input states" using tx.inputStates.isEmpty()
                "CreateData command should have a single output state of type GroupDataAssociationState" using
                        (groupDataAssociationStateOutputs.size == 1)
                "CreateData command should have mandatory value & participant field" using
                        (groupDataAssociationStateOutputs.single().participants.isNotEmpty())
            }
            is Commands.UpdateGroupParticipants -> {
                requireThat {
                    "UpdateGroupParticipants command should have a single input state of type GroupDataAssociationState" using (groupDataAssociationStateInputs.size == 1)
                    "UpdateGroupParticipants command should have a single output state of type GroupDataAssociationState" using (groupDataAssociationStateOutputs.size == 1)
                    "UpdateGroupParticipants command should have same data identifier" using
                            (groupDataAssociationStateOutputs.single().linearId == groupDataAssociationStateInputs.single().linearId)
                    "UpdateGroupParticipants should not change the value" using
                            (groupDataAssociationStateOutputs.single().value == groupDataAssociationStateInputs.single().value)
                }
            }
            is Commands.UpdateGroupData -> {
                requireThat {
                    "UpdateGroupData command should have a single input state of type GroupDataAssociationState" using (groupDataAssociationStateInputs.size == 1)
                    "UpdateGroupData command should have a single output state of type GroupDataAssociationState" using (groupDataAssociationStateOutputs.size == 1)
                    "UpdateGroupData command should have same data identifier" using
                            (groupDataAssociationStateOutputs.single().linearId == groupDataAssociationStateInputs.single().linearId)
                    "UpdateGroupData should not change the participants" using
                            (groupDataAssociationStateOutputs.single().participants == groupDataAssociationStateInputs.single().participants
                                    && groupDataAssociationStateInputs.single().associatedGroupStates == groupDataAssociationStateOutputs.single().associatedGroupStates)
                }
            }
        }
    }

    interface Commands : CommandData {
        class CreateData : TypeOnlyCommandData(), Commands
        class UpdateGroupParticipants : TypeOnlyCommandData(), Commands
        class UpdateGroupData: TypeOnlyCommandData(), Commands
    }
}