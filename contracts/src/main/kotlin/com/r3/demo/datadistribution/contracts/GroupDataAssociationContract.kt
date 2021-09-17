package com.r3.demo.datadistribution.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class GroupDataAssociationContract: Contract {

    companion object {
        const val ID = "com.r3.demo.datadistribution.contracts.GroupDataAssociationContract"
    }

    override fun verify(tx: LedgerTransaction)  {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.CreateData -> {}
            is Commands.AddGroupAssociation -> {
                requireThat {
                    "AddGroupAssociation command should not have "
                }
            }

        }

    }

    interface Commands: CommandData {
        class CreateData : TypeOnlyCommandData(), Commands
        class AddGroupAssociation: TypeOnlyCommandData(), Commands
    }
}