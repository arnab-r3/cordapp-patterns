package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.evolvable.addCreateEvolvableToken
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.TokenDefinition
import com.r3.demo.generic.argFail
import com.r3.demo.generic.getPreferredNotaryForToken
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

object NFTFlows {


    @CordaSerializable
    data class Notification(val signatureRequired: Boolean = false)

    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class DefineNFTFlow(
        private val tokenDefinition: TokenDefinition,
        private val observers: List<Party> = emptyList()
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            val (tokenTypeName, tokenState) = when (tokenDefinition) {
                is KittyTokenDefinition -> {
                    "KITTY" to tokenDefinition.toKittyToken(serviceHub)
                }
                else -> argFail("Unable to find the token definition")
            }
            val notary = getPreferredNotaryForToken(tokenTypeName)
            val transactionBuilder = TransactionBuilder(notary)
            addCreateEvolvableToken(transactionBuilder, tokenState, notary)

            val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

            // everyone who is a participant but not a maintainer is an observer
            val observers = (observers + tokenState.participants) - tokenState.maintainers - ourIdentity
            val observerSessions = observers.map { initiateFlow(it) }

            // the maintainers except us
            val maintainersExceptUs = tokenState.maintainers - ourIdentity
            val maintainersExceptUsSessions = maintainersExceptUs.map { initiateFlow(it) }


            maintainersExceptUsSessions.forEach {
                it.send(Notification(signatureRequired = true))
            }


            val signedTransaction = subFlow(CollectSignaturesFlow(
                sessionsToCollectFrom = maintainersExceptUsSessions,
                partiallySignedTx = partiallySignedTransaction))

            observerSessions.forEach {
                it.send(Notification(signatureRequired = false))
            }

            return subFlow(ObserverAwareFinalityFlow(signedTransaction = signedTransaction,
                allSessions = maintainersExceptUsSessions + observerSessions))

        }


    }

    @InitiatedBy(DefineNFTFlow::class)
    class DefineNFTFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notification = otherSession.receive<Notification>().unwrap { it }
            // Sign the transaction proposal, if required
            if (notification.signatureRequired) {
                val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        // TODO
                    }
                }
                subFlow(signTransactionFlow)
            }

            // Resolve the creation transaction.
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }

    }


    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class IssueNFTFlow(
        private val tokenIdentifier: String,
        private val tokenClass: Class<out EvolvableTokenType>,
        private val tokenQuantity: Long,
        private val receivingParty: AbstractParty) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val queryCriteria = QueryCriteria
                .LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    contractStateTypes = setOf(tokenClass)
                )

            val evolvableTokens = serviceHub.vaultService.queryBy<EvolvableTokenType>(queryCriteria)

            require(evolvableTokens.totalStatesAvailable > 0) {"Unable to find any evolvable tokens with identifier $tokenIdentifier"}

            val tokenToIssue = evolvableTokens.states.single().state.data

            val tokenPointer = tokenToIssue.toPointer(tokenClass)

            val issuedTokenType = tokenQuantity of tokenPointer issuedBy ourIdentity heldBy receivingParty

            return subFlow(
                IssueTokensFlowWithNotarySelection(
                    token = issuedTokenType,
                    participantSessions = sessionsForParticipants(listOf(tokenToIssue)))
            )
        }
    }

    @InitiatedBy(IssueNFTFlow::class)
    class IssueNFTFlowHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            return subFlow(IssueTokensFlowWithNotarySelectionHandler(otherPartySession))
        }


    }



}