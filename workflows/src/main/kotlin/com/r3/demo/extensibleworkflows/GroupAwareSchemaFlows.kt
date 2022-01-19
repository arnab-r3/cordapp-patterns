package com.r3.demo.extensibleworkflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.custom.Schema
import com.r3.demo.custom.SchemaContract
import com.r3.demo.custom.SchemaState
import com.r3.demo.datadistribution.flows.GroupDataAssociationFlows
import com.r3.demo.datadistribution.flows.GroupDataManagementFlow
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.core.contracts.StatePointer
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object CreateGroupDataAssociationAndLinkSchema {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val groupIds: Set<String>,
        private val schema: Schema
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
            object BUILDING_THE_SCHEMA_TX : ProgressTracker.Step("Building the schema transaction.")
            object VERIFYING_THE_SCHEMA_TX : ProgressTracker.Step("Verifying schema transaction.")

            object COLLECTING_SIGS_AND_FINALITY_FOR_SCHEMA_TX : ProgressTracker.Step("Collecting Signatures & Executing Finality for Schema Transaction") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object ASSOCIATING_SCHEMA_WITH_GROUPS :
                ProgressTracker.Step("Associating schema with group metadata") {
                override fun childProgressTracker(): ProgressTracker =
                    GroupDataAssociationFlows.CreateNewAssociationState.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                BUILDING_THE_SCHEMA_TX,
                VERIFYING_THE_SCHEMA_TX,
                COLLECTING_SIGS_AND_FINALITY_FOR_SCHEMA_TX,
                ASSOCIATING_SCHEMA_WITH_GROUPS)
        }

        override val progressTracker = tracker()


        @Suspendable
        override fun call(): String {

            // if the groupIds are not empty then populate the participants with the Data Distribution permissions,
            // otherwise add ourself
            progressTracker.currentStep = FETCHING_GROUP_DETAILS
            val groupsParticipants = getGroupsParticipants(groupIds)

            val maintainersFromSchemaMetadata = SchemaState.getParticipantsFromSchema(serviceHub, schema)

            // check if all the maintainers are inside the group, containsAll is so confusing, ughh!
            if (!groupsParticipants.containsAll(maintainersFromSchemaMetadata))
                flowFail("Schema cannot be maintained by parties who are not in either of the groups $groupIds")


            val signingKeys = maintainersFromSchemaMetadata.map { it.owningKey }

            progressTracker.currentStep = BUILDING_THE_SCHEMA_TX
            val outputSchemaState = SchemaState(schema, maintainersFromSchemaMetadata)

            // associate some metadata to the GroupDataAssociationState
            val metaDataMap = mapOf(
                "associationType" to "Schema",
                "schemaId" to schema.id.toString(),
                "groupIds" to groupIds.joinToString()
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputSchemaState)
                .addCommand(SchemaContract.Commands.CreateSchema(), signingKeys)

            progressTracker.currentStep = VERIFYING_THE_SCHEMA_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY_FOR_SCHEMA_TX
            val signedSchemaTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                signers = maintainersFromSchemaMetadata.toSet(),
                participants = maintainersFromSchemaMetadata.toSet()
            ))


            progressTracker.currentStep = ASSOCIATING_SCHEMA_WITH_GROUPS

            val committedSchemaTxRef =
                signedSchemaTx.toLedgerTransaction(serviceHub, true)

                .findOutRef<SchemaState> { it.schema.id == outputSchemaState.id }
            val staticPointer = StatePointer.staticPointer(committedSchemaTxRef)


            val subFlowResponse = subFlow(GroupDataAssociationFlows.CreateNewAssociationState(
                setOf(staticPointer), metaDataMap, groupIds)
            )

            return "Created Schema with id ${schema.id} and following are the group association details.\n" + subFlowResponse

        }

    }

    // TODO add provision to delete schema

}