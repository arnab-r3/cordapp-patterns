package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.DataAdminRole
import net.corda.bn.flows.*
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

object MembershipFlows {

    /**
     * Create a new network and assign the BNO role to myself.
     */
    @StartableByRPC
    class CreateMyNetworkFlow(private val defaultGroupName: String) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val signedTransaction = subFlow(CreateBusinessNetworkFlow(
                groupName = defaultGroupName,
                notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY))
            ))
            val networkId = signedTransaction.coreTransaction.outputsOfType<MembershipState>().single().networkId
            val membershipId = signedTransaction.coreTransaction.outputsOfType<MembershipState>().single().linearId
            return "Created Network with ID: $networkId, membershipId: ${membershipId.id}, and role BNO"
        }
    }

    @StartableByRPC
    class AssignDataAdminRoleFlow
        (private val membershipId: String) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ModifyRolesFlow(
                membershipId = UniqueIdentifier.fromString(membershipId),
                roles = setOf(DataAdminRole()),
                notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY))))
        }
    }


    @StartableByRPC
    class QueryRolesForMembershipFlow(private val membershipId: String) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String =
            serviceHub.cordaService(BNService::class.java).getMembership(UniqueIdentifier.fromString(membershipId))
                ?.run {
                    state.data.roles.joinToString { it.name }
                } ?: "Membership with $membershipId not found"
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
        override fun call(): String =
            serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(BNO_PARTY))?.let { party ->
                val signedTransaction = subFlow(
                    RequestMembershipFlow(
                        authorisedParty = party,
                        networkId = networkId,
                        notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(DEFAULT_NOTARY)))
                )
                val membershipId = signedTransaction.coreTransaction.outputsOfType<MembershipState>().single().linearId
                "Created membership request for Network $networkId with membershipId : $membershipId. Please share this with the BNO of the network"
            } ?: "BNO Party $BNO_PARTY not found"

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
            return "Group created with id: $groupId"
        }
    }
}
