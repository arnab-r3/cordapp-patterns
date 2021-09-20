package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.common.canDistributeData
import net.corda.bn.flows.BNService
import net.corda.bn.flows.MembershipManagementFlow
import net.corda.bn.states.GroupState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
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
    class DistributeTransactionToNetworkFlow(
        private val signedTransaction: SignedTransaction,
        private val networkId: String,
        private val groupFilterCriteria: (GroupState) -> Boolean = { true }
    ) : MembershipManagementFlow<Unit>() {

        @Suspendable
        override fun call() {
            val bnService = serviceHub.cordaService(BNService::class.java)
            // basic authorization whether the participant who is distributing is a part of the network
            authorise(networkId = networkId, BNService = bnService) { it.canDistributeData() }

            val allParties =
                bnService
                    .getAllBusinessNetworkGroups(networkId)
                    .map { it.state.data }.filter(groupFilterCriteria)
                    .flatMap { it.participants }
                    .toSet()

            serviceHub.cordaService(DistributionService::class.java).distributeTransactionParallel(
                signedTransaction, allParties
            )
        }

    }


    @Suppress("unused")
    @InitiatingFlow
    class DistributeTransactionToGroupFlow(
        private val signedTransaction: SignedTransaction,
        private val groupId: String,
        private val partyFilterCriteria: (Party) -> Boolean = { true }
    ) : MembershipManagementFlow<Unit>() {

        val log: Logger = LoggerFactory.getLogger("com.r3.demo.datadistribution.flows")

        @Suspendable
        override fun call() {
            val bnService = serviceHub.cordaService(BNService::class.java)
            // basic authorization whether the participant who is distributing is a part of the network
            bnService.getBusinessNetworkGroup(groupId = UniqueIdentifier.fromString(groupId))?.apply {
                // check if the invoker is a part of the network

                log.info("Authorizing ${ourIdentity.name} to distribute transaction ${signedTransaction.id} to group : $groupId")

                authorise(state.data.networkId, bnService) { it.canDistributeData() }

                require(ourIdentity in state.data.participants) { "Our identity is not a part of the group" }
                val recipientParties = state.data.participants.filter(partyFilterCriteria).toSet()
                val distributionService = serviceHub.cordaService(DistributionService::class.java)
                distributionService.distributeTransactionParallel(signedTransaction, recipientParties)
            }
        }
    }


}
