package com.r3.demo.extensibleworkflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.custom.Schema
import com.r3.custom.SchemaState
import com.r3.demo.datadistribution.contracts.GroupDataAssociationContract
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.datadistribution.flows.GroupDataAssociationFlows
import com.r3.demo.datadistribution.flows.MembershipBroadcastFlows
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.generic.linearPointer
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.bn.states.GroupState
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
    ): GroupDataAssociationFlows.GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
//            {
//                override fun childProgressTracker(): ProgressTracker = GroupDataManagementFlow.tracker()
//            }

            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker = MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
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
            val groupDataParticipants = getGroupDataParticipants(groupIds)

            progressTracker.currentStep = BUILDING_THE_TX
            val groupLinearPointers =
                groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()


            val outputSchemaState = SchemaState(schema, groupDataParticipants.toList())

            val outputGroupDataAssociationState = GroupDataAssociationState(
                value = schema.id,
                associatedGroupStates = groupLinearPointers,
                participants = groupDataParticipants.toList())


            val signers = groupDataParticipants + ourIdentity
            val signerKeys = signers.map { it.owningKey }


            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputGroupDataAssociationState)
                .addOutputState(outputSchemaState)
                .addCommand(GroupDataAssociationContract.Commands.CreateData(), signerKeys)


            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)


            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupDataParticipants
            ))


            progressTracker.currentStep = GroupDataAssociationFlows.CreateDataFlow.Companion.DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members except the group data participants, because they already have the transaction
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions= listOf(finalizedTx),
                    groupId = groupId) { it !in groupDataParticipants }
                )
            }

            return "Schema with ID: ${outputSchemaState.id} & " +
                    "association state with id: ${outputGroupDataAssociationState.linearId} " +
                    "created and distributed to groups: ${groupIds?.joinToString()}, TxId: ${finalizedTx.id}"

        }

    }

}