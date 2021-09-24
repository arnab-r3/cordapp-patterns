package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.common.canDistributeData
import com.r3.demo.common.canManageData
import com.r3.demo.datadistribution.contracts.GroupDataAssociationContract
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.datadistribution.flows.MembershipBroadcastFlows.DistributeTransactionToGroupFlow
import com.r3.demo.generic.argFail
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.generic.linearPointer
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.bn.flows.BNService
import net.corda.bn.flows.MembershipManagementFlow
import net.corda.bn.states.GroupState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

object GroupDataAssociationFlows {

    abstract class GroupDataManagementFlow<T> : MembershipManagementFlow<T>() {

        fun getGroupDataParticipants(groupIds: Set<String>?): Set<Party> {

            val bnService = serviceHub.cordaService(BNService::class.java)
            return groupIds?.flatMap { groupId ->

                val groupParticipants = mutableSetOf<Party>()
                bnService.getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))?.apply {
                    // check if we are a part of the network and have data admin role
                    authorise(state.data.networkId, bnService) { it.canManageData() && it.canDistributeData() }
                    val dataDistributionParties = state.data.participants.filter { party ->
                        val membershipState = bnService.getMembership(state.data.networkId, party)?.state?.data
                        membershipState?.canDistributeData() ?: false
                    }
                    groupParticipants.addAll(dataDistributionParties)
                } ?: argFail("Group $groupId does not exist")

                (groupParticipants + ourIdentity).toSet()

            }?.toSet() ?: setOf(ourIdentity)
        }
    }

    /**
     * Create Data item by the data administrator and distribute to the groups of participants.
     */
    @StartableByRPC
    class CreateDataFlow(
        private val data: String,
        private val groupIds: Set<String>?
    ) : GroupDataManagementFlow<String>() {


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
                override fun childProgressTracker(): ProgressTracker = DistributeTransactionToGroupFlow.tracker()
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
                groupIds?.map { linearPointer(it, GroupState::class.java) }?.toSet()

            val outputState = GroupDataAssociationState(
                value = data,
                associatedGroupStates = groupLinearPointers,
                participants = groupDataParticipants.toList())

            val signers = groupDataParticipants + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputState)
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


            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members except the group data participants, because they already have the transaction
            groupIds?.forEach { groupId ->
                subFlow(DistributeTransactionToGroupFlow(
                    signedTransaction = finalizedTx,
                    groupId = groupId) { it !in groupDataParticipants }
                )
            }

            return "Data with id: ${outputState.linearId} created and distributed to groups: ${groupIds?.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }

    /**
     * Flow to add groups to
     */
    @StartableByRPC
    class UpdateDataParticipantsFlow(
        private val dataIdentifier: String,
        private val newGroupIds: Set<String>
    ) : GroupDataManagementFlow<String>() {

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
                override fun childProgressTracker(): ProgressTracker = DistributeTransactionToGroupFlow.tracker()
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

            val bnService = serviceHub.cordaService(BNService::class.java)

            val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
                .withUuid(listOf(UUID.fromString(dataIdentifier)))
                .withStatus(Vault.StateStatus.UNCONSUMED)
                .withRelevancyStatus(Vault.RelevancyStatus.ALL)

            val groupDataAssociationStateRef =
                serviceHub.vaultService.queryBy<GroupDataAssociationState>(linearStateQueryCriteria).states.single()


            val groupIds =
                groupDataAssociationStateRef.state.data.associatedGroupStates?.toSet()?.map { linearPointer ->

                    val groupId = linearPointer.pointer.id.toString()
                    val businessNetworkGroup = bnService
                        .getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))
                        ?: argFail("Group $groupId does not exist")
                    // check if our identity can manage and distribute data for the group
                    authorise(businessNetworkGroup.state.data.networkId,
                        bnService) { it.canManageData() && it.canDistributeData() }
                    groupId

                }?.let {
                    (it + newGroupIds).toSet()
                } ?: newGroupIds.toSet()

            progressTracker.currentStep = FETCHING_GROUP_DETAILS

            val groupParticipants = getGroupDataParticipants(groupIds)

            progressTracker.currentStep = BUILDING_THE_TX
            val signers = groupParticipants + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val groupLinearPointers = groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = groupDataAssociationStateRef.state.data.copy(
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipants.toList()
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addInputState(groupDataAssociationStateRef)
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.UpdateGroups(), signerKeys)


            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupParticipants
            ))

            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members
            groupIds.forEach { groupId ->
                subFlow(DistributeTransactionToGroupFlow(
                    signedTransaction = finalizedTx,
                    groupId = groupId) { it != ourIdentity }
                )
            }

            return "Data with id: ${outputState.linearId} updated and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }
}
