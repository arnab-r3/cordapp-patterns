package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.common.canDistributeData
import net.corda.bn.flows.BNService
import net.corda.bn.flows.MembershipManagementFlow
import net.corda.bn.states.GroupState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Broadcasts a signed transaction to members in a network or a group
 */
@Suppress("unused")
object MembershipBroadcastFlows {


    /**
     * Distribute a transaction to all parties inside groups inside a business network that the caller is aware of.
     * The data admin is supposed to call this flow as it can be authorised to ask the BNO for all group data.
     */
    @Suppress("unused")
    @InitiatingFlow
    class DistributeTransactionsToNetworkFlow(
        private val signedTransactions: List<SignedTransaction>,
        private val networkId: String,
        private val groupFilterCriteria: (GroupState) -> Boolean = { true }
    ) : MembershipManagementFlow<Unit>() {

        companion object {
            object AUTHORIZING_DISTRIBUTION_PERMISSIONS : ProgressTracker.Step("Fetching Group Details in Network")
            object DISTRIBUTING_PARALLELY : ProgressTracker.Step("Distributing data to all participants in in parallel")

            fun tracker() = ProgressTracker(AUTHORIZING_DISTRIBUTION_PERMISSIONS, DISTRIBUTING_PARALLELY)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call() {
            val bnService = serviceHub.cordaService(BNService::class.java)

            progressTracker.currentStep = AUTHORIZING_DISTRIBUTION_PERMISSIONS
            // basic authorization whether the participant who is distributing is a part of the network
            authorise(networkId = networkId, BNService = bnService) { it.canDistributeData() }

            val allParties =
                bnService
                    .getAllBusinessNetworkGroups(networkId)
                    .map { it.state.data }.filter(groupFilterCriteria)
                    .flatMap { it.participants }
                    .toSet()

            progressTracker.currentStep = DISTRIBUTING_PARALLELY
            serviceHub.cordaService(DistributionService::class.java).distributeTransactionsParallel(
                signedTransactions, allParties
            )
        }

    }


    /**
     * Distribute a transaction to all parties in a group
     * Optionally accept a lambda to filter the parties who should get the transaction
     */
    @Suppress("unused")
    @InitiatingFlow
    class DistributeTransactionsToGroupFlow(
        private val signedTransactions: List<SignedTransaction> = listOf(),
        private val stateAndRefs : Set<StateAndRef<ContractState>> = setOf(),
        private val groupId: String,
        private val partyFilterCriteria: (Party) -> Boolean = { true }
    ) : MembershipManagementFlow<Unit>() {

        companion object {
            object AUTHORIZING_DISTRIBUTION_PERMISSIONS : ProgressTracker.Step("Fetching Group Details")
            object DISTRIBUTING_PARALLELY : ProgressTracker.Step("Distributing data to other participants in parallel")

            fun tracker() = ProgressTracker(AUTHORIZING_DISTRIBUTION_PERMISSIONS, DISTRIBUTING_PARALLELY)
        }

        override val progressTracker = tracker()

        val log: Logger = LoggerFactory.getLogger("com.r3.demo.datadistribution.flows")

        @Suspendable
        override fun call() {
            val bnService = serviceHub.cordaService(BNService::class.java)
            // basic authorization whether the participant who is distributing is a part of the network
            bnService.getBusinessNetworkGroup(groupId = UniqueIdentifier.fromString(groupId))?.apply {
                // check if the invoker is a part of the network

                log.info("Authorizing ${ourIdentity.name} to distribute transaction to group : $groupId")

                progressTracker.currentStep = AUTHORIZING_DISTRIBUTION_PERMISSIONS
                authorise(state.data.networkId, bnService) { it.canDistributeData() }

                require(ourIdentity in state.data.participants) { "Our identity is not a part of the group" }
                val recipientParties = state.data.participants.filter(partyFilterCriteria).toSet()

                progressTracker.currentStep = DISTRIBUTING_PARALLELY

                serviceHub.cordaService(DistributionService::class.java)
                    .distributeTransactionsParallel(signedTransactions, recipientParties)

                serviceHub.cordaService(DistributionService::class.java)
                    .distributeStateAndRefsParallel(stateAndRefs, recipientParties)
            }
        }
    }
}
