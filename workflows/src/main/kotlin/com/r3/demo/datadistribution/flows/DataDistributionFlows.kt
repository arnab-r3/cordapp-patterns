package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.flows.*
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction


object MembershipFlows {

    @StartableByRPC
    class CreateMyNetworkFlow(private val groupName: String) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val signedTransaction = subFlow(CreateBusinessNetworkFlow(
                groupName = groupName,
                notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY))
            ))
            val networkId = signedTransaction.coreTransaction.outputsOfType<MembershipState>().single().networkId
            return "Created Network with ID: $networkId"
        }
    }

    @StartableByRPC
    class AssignDataAdminRoleFlow
        (private val membershipId: UniqueIdentifier,
         private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ModifyRolesFlow(membershipId, setOf(DataAdminRole()), notary))
        }
    }

    @StartableByRPC
    class OnboardMyNetworkParticipant(
        private val networkId: String,
        private val onboardedParty: Party
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() = subFlow(
            OnboardMembershipFlow(
                networkId = networkId,
                onboardedParty = onboardedParty,
                businessIdentity = null,
                notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY))
            )
        )
    }

    @StartableByRPC
    class RequestMyNetworkMembership(
        private val networkId: String
    ) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val signedTransaction = subFlow(
                RequestMembershipFlow(authorisedParty = serviceHub.identityService.partiesFromName(BNO_PARTY, true)
                    .single(),
                    networkId = networkId,
                    notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY)))
            )
            val membershipId = signedTransaction.coreTransaction.outputsOfType<MembershipState>().single().linearId

            return "Created membership request for Network $networkId with membershipId : $membershipId. Please share this with the BNO of the network"
        }
    }

    @StartableByRPC
    class ApproveMyNetworkMembership(
        private val membershipId: String
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() = subFlow(
            ActivateMembershipFlow(membershipId = UniqueIdentifier.fromString(membershipId),
                notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY))))
    }


    @StartableByRPC
    class CreateMyGroupFlow(
        private val networkId: String,
        private val groupName: String,
        private val membershipIds: List<String>
    ) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val signedTransaction = subFlow(CreateGroupFlow(
                networkId = networkId,
                groupName = groupName,
                additionalParticipants = membershipIds.map { UniqueIdentifier.fromString(it) }.toSet()))
            val groupId = signedTransaction.coreTransaction.outputsOfType<GroupState>().single().linearId
            return "Group created with type: $groupId"
        }
    }
}


object DataBroadCastFlows {

    // reference: https://lankydan.dev/broadcasting-a-transaction-to-external-organisations
    @StartableByRPC
    @InitiatingFlow
    class InitiatorFlow(
        private val signedTransaction: SignedTransaction,
        private val counterParty: Party
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val flowSession = initiateFlow(counterParty)
            subFlow(SendTransactionFlow(flowSession, signedTransaction))
        }
    }


    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }


}

object MembershipBroadcastHelperFlows {

    @InitiatingFlow
    class DistributeTransactionsToNetworkFlow(
        private val signedTransaction: SignedTransaction,
        private val networkId: String,
        private val filterCriteria: (Party) -> Boolean = {true}
    ) : MembershipManagementFlow<Unit>() {


        @Suspendable
        override fun call(): Unit {
            val bnService = serviceHub.cordaService(BNService::class.java)
            // basic authorization whether the participant who is distributing is a part of the network
            authorise(networkId = networkId, BNService = bnService) { true }
            val allParties =
                bnService.getAllBusinessNetworkGroups(networkId).flatMap { it.state.data.participants }.filter(filterCriteria).toSet()
            val distributionService = serviceHub.cordaService(DistributionService::class.java)
            distributionService.distributeTransactionParallel(signedTransaction, allParties)
        }

    }


    @InitiatingFlow
    class DistributeTransactionsToGroupFlow(
        private val signedTransaction: SignedTransaction,
        private val groupId: String,
        private val filterCriteria: (Party) -> Boolean = {true}
    ) : MembershipManagementFlow<Unit>() {


        @Suspendable
        override fun call(): Unit {
            val bnService = serviceHub.cordaService(BNService::class.java)
            // basic authorization whether the participant who is distributing is a part of the network
            bnService.getBusinessNetworkGroup(groupId = UniqueIdentifier.fromString(groupId))?.apply {
                // check if the invoker is a part of the network
                authorise(state.data.networkId, bnService){true}
                require (ourIdentity in state.data.participants) {"Our identity is not a part of the group"}
                val recipientParties = state.data.participants.filter(filterCriteria).toSet()
                val distributionService = serviceHub.cordaService(DistributionService::class.java)
                distributionService.distributeTransactionParallel(signedTransaction, recipientParties)
            }
        }
    }




}

