package com.r3.demo.extensibleworkflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.custom.Schema
import com.r3.custom.SchemaContract
import com.r3.custom.SchemaState
import com.r3.demo.common.canManageData
import com.r3.demo.datadistribution.flows.GroupDataAssociationFlows
import com.r3.demo.datadistribution.flows.GroupDataManagementFlow
import com.r3.demo.datadistribution.flows.MembershipBroadcastFlows
import com.r3.demo.generic.getDefaultNotary
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object CreateGroupAwareSchema {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val groupIds: Set<String>,
        val schema: Schema
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker =
                    MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                BUILDING_THE_TX,
                VERIFYING_THE_TX,
                COLLECTING_SIGS_AND_FINALITY,
                DISTRIBUTING_GROUP_DATA)
        }

        override val progressTracker = tracker()


        @Suspendable
        override fun call(): String {

            // if the groupIds are not empty then populate the participants with the Data Distribution permissions,
            // otherwise add ourself
            progressTracker.currentStep = FETCHING_GROUP_DETAILS
            val partiesWithDataManagementRights = getGroupsParticipants(groupIds) {
                it.canManageData()
            }

            val signingKeys = partiesWithDataManagementRights.map { it.owningKey }

            progressTracker.currentStep = BUILDING_THE_TX

            val outputSchemaState = SchemaState(schema, partiesWithDataManagementRights.toList())

            // associate some metadata to the GroupDataAssociationState
            val metaDataMap = mapOf(
                "associationType" to "Schema",
                "schemaId" to schema.id.toString(),
                "groupIds" to groupIds.joinToString()
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputSchemaState)
                .addCommand(SchemaContract.Commands.CreateSchema(), signingKeys)

            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY

            subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = partiesWithDataManagementRights,
                participants = partiesWithDataManagementRights
            ))

            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            val subFlowResponse = subFlow(GroupDataAssociationFlows.CreateDataFlow(txBuilder, metaDataMap, groupIds))

            return "Created Schema with id ${schema.id} and following are the group association details.\n" + subFlowResponse

        }

    }

}