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
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import java.util.*

object GroupDataAssociationFlows {


    /**
     * Create Data item by the data administrator and distribute to the groups of participants.
     */
    @StartableByRPC
    @InitiatingFlow
    class CreateDataFlow(
        private val data: String,
        private val groupIds: List<String>?
    ) : MembershipManagementFlow<String>() {

        @Suspendable
        override fun call(): String {

            val bnService = serviceHub.cordaService(BNService::class.java)

            val groupParticipants = groupIds?.flatMap { groupId ->

                val groupParticipants = mutableListOf<Party>()
                bnService.getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))?.apply {

                    // check if we are a part of the network and have data admin role
                    authorise(state.data.networkId, bnService) { it.canManageData() && it.canDistributeData() }

                    groupParticipants.addAll(state.data.participants)

                } ?: argFail("Group $groupId does not exist")

                (groupParticipants + ourIdentity) as List<Party>

            }?: listOf(ourIdentity)

            val groupLinearPointers =
                groupIds?.map { linearPointer(it, GroupState::class.java) }?.toSet()

            val outputState = GroupDataAssociationState(
                value = data,
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipants)

            val signers = groupParticipants - ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.CreateData(), signerKeys)

            txBuilder.verify(serviceHub)

            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupParticipants
            ))

            // distribute the transaction to all group members
            groupIds?.forEach { groupId ->
                subFlow(DistributeTransactionToGroupFlow(
                    signedTransaction = finalizedTx,
                    groupId = groupId)
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
        private val newGroupIds: List<String>
    ) : MembershipManagementFlow<String>() {

        @Suspendable
        override fun call(): String {

            val bnService = serviceHub.cordaService(BNService::class.java)
            val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
                .withUuid(listOf(UUID.fromString(dataIdentifier)))
                .withStatus(Vault.StateStatus.UNCONSUMED)
                .withRelevancyStatus(Vault.RelevancyStatus.ALL)

            val groupDataAssociationStateRef =
                serviceHub.vaultService.queryBy<GroupDataAssociationState>(linearStateQueryCriteria).states.single()


            val groupIds = groupDataAssociationStateRef.state.data.associatedGroupStates?.map { linearPointer ->

                val groupId = linearPointer.pointer.id.toString()
                val businessNetworkGroup = bnService
                    .getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))
                    ?: argFail("Group $groupId does not exist")
                // check if our identity can manage and distribute data for the group
                authorise(businessNetworkGroup.state.data.networkId,
                    bnService) { it.canManageData() && it.canDistributeData() }
                groupId

            }?.let {
                it + newGroupIds
            } ?: newGroupIds

            val groupParticipants = groupIds.flatMap { groupId ->
                val businessNetworkGroup = bnService
                    .getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))
                    ?: argFail("Group $groupId does not exist")
                businessNetworkGroup.state.data.participants
            }

            val signers = groupParticipants - ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val groupLinearPointers = groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = groupDataAssociationStateRef.state.data.copy(
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipants + ourIdentity
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addInputState(groupDataAssociationStateRef)
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.UpdateGroups(), signerKeys)


            txBuilder.verify(serviceHub)

            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupParticipants
            ))

            // distribute the transaction to all group members
            groupIds.forEach { groupId ->
                subFlow(DistributeTransactionToGroupFlow(
                    signedTransaction = finalizedTx,
                    groupId = groupId)
                )
            }

            return "Data with id: ${outputState.linearId} updated and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }
}


// TODO write responder flow to check if the sent tx can be accepted by checking if I am a part of any of the groups mentioned
// todo write update logic to update the list of groups and thereby participants
// todo write logic (contract) to check if the correct data is used (check in contract)
// todo write integration tests
// check data distribution
// check usage permissions
