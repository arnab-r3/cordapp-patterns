package com.r3.demo.custom

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

interface ExtensibleWorkflowContract : Contract

class SchemaBackedKVContract : ExtensibleWorkflowContract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {
        val outputSchemaBackedKVStates = tx.outputsOfType<SchemaBackedKVState>()
        val command = tx.commands.requireSingleCommand<Commands>()

        // TODO check if the operation on the KV is performed only be maintainers of the KV

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


class SchemaContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        val outputSchemaStates = tx.outputsOfType<SchemaState>()
        val inputSchemaStates = tx.inputsOfType<SchemaState>()
        // TODO check if the schema management is performed only by BNOs. Add reference state to group states and group data association states
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
           is Commands.CreateSchema -> {
               requireThat {
                   "Command Create schema should not have any inputs" using inputSchemaStates.isEmpty()
                   "Command Create schema should have exactly one output" using (outputSchemaStates.size == 1)
               }
           }
            is Commands.DeleteSchema -> {
                requireThat {
                    "Command Delete schema should not have any outputs" using outputSchemaStates.isEmpty()
                    "Command Delete schema should have exactly one input" using (inputSchemaStates.size == 1)
                }
            }
        }
    }

    interface Commands : CommandData {
        class CreateSchema : TypeOnlyCommandData(), Commands
        class DeleteSchema : TypeOnlyCommandData(), Commands
    }
}