package com.r3.custom

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

interface ExtensibleWorkflowContract : Contract

class SchemaBackedKVContract : ExtensibleWorkflowContract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {
        val outputSchemaBackedKVStates = tx.outputsOfType<SchemaBackedKVState>()
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            // add additional validations alongside the schema validations
            is Commands.CreateData -> outputSchemaBackedKVStates.single().validateSchema("Create", tx, null)
            is Commands.UpdateData -> outputSchemaBackedKVStates.single().validateSchema("Update", tx, null)
        }
    }

    interface Commands : CommandData {
        class CreateData : TypeOnlyCommandData(), Commands
        class UpdateData : TypeOnlyCommandData(), Commands
    }

}