package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.datadistribution.contracts.GroupDataAssociationContract
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.generic.argFail
import com.r3.demo.generic.authFail
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.generic.linearPointer
import net.corda.bn.flows.BNService
import net.corda.bn.states.GroupState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

object GroupDataAssociationFlows {

    @StartableByRPC
    @InitiatingFlow
    class CreateDataFlow(
        private val data: String,
        private val groups: List<String>
    ) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {

            val bnService = serviceHub.cordaService(BNService::class.java)
            val groupParticipants = groups.flatMap { groupId ->

                val groupParticipants = mutableListOf<Party>()

                bnService.getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))?.apply {

                    groupParticipants.addAll(state.data.participants)

                    if (!bnService.isBusinessNetworkMember(state.data.networkId, ourIdentity)) {
                        authFail("Our identity is not a part of network ${state.data.networkId} which is a part of group $groupId ")
                    }

                } ?: argFail("Group $groupId does not exist")


                groupParticipants as List<Party>
            }

            val groupLinearPointers =
                groups.map{ linearPointer(it, GroupState::class.java)}.toSet()

            val outputState = GroupDataAssociationState(
                value = data,
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipants)

            val signerKeys = groupParticipants.map { it.owningKey }

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.CreateData(), signerKeys)

            txBuilder.verify(serviceHub)

            serviceHub.signInitialTransaction(txBuilder)

            // todo separate signer (data admin) from recipients.
            // todo finalize txn

            return ""
        }

    }


}

// TODO write responder flow to check if the sent tx can be accepted by checking if I am a part of any of the groups mentioned
// todo write update logic to update the list of groups and thereby participants
// todo write logic (contract) to check if the correct data is used (check in contract)
// todo write integration tests
    // check data distribution
    // check usage permissions
