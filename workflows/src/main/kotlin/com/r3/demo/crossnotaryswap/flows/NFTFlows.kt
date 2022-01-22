package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.evolvable.addCreateEvolvableToken
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.TokenDefinition
import com.r3.demo.generic.argFail
import com.r3.demo.generic.getPreferredNotaryForToken
import com.r3.demo.generic.requireInFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
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

            val (_, tokenState) = when (tokenDefinition) {
                is KittyTokenDefinition -> {
                    "KITTY" to tokenDefinition.toKittyToken(serviceHub)
                }
                else -> argFail("Unable to find the token definition")
            }
            val notary = getPreferredNotaryForToken(tokenState.toPointer(tokenState.javaClass))
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
        private val receivingParty: Party
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val queryCriteria = QueryCriteria
                .LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    contractStateTypes = setOf(EvolvableTokenType::class.java)
                )
            val evolvableTokens = serviceHub.vaultService.queryBy<EvolvableTokenType>(queryCriteria)
            require(evolvableTokens.states.isNotEmpty()) { "Unable to find any evolvable tokens with identifier $tokenIdentifier" }
            val tokenToIssue = evolvableTokens.states.single().state.data
            val tokenPointer = tokenToIssue.toPointer(tokenToIssue.javaClass)
            val issuedTokenType = tokenPointer issuedBy ourIdentity heldBy receivingParty
            return subFlow(
                IssueTokensFlowWithNotarySelection(
                    token = issuedTokenType,
                    participantSessions = sessionsForParticipants(listOf(issuedTokenType)))
            )
        }
    }

    @InitiatedBy(IssueNFTFlow::class)
    class IssueNFTFlowHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = subFlow(IssueTokensFlowWithNotarySelectionHandler(otherPartySession))
    }


    @InitiatingFlow
    @StartableByRPC
    class MoveNFT(
        private val tokenIdentifier: String,
        private val receivingParty: Party,
        private val observers: List<Party> = emptyList(),
        private val queryCriteria: QueryCriteria? = null
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val linearQueryCriteria = QueryCriteria
                .LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    contractStateTypes = setOf(FungibleToken::class.java)
                )
            val fungibleTokens = serviceHub.vaultService.queryBy<FungibleToken>(linearQueryCriteria)
            requireInFlow(fungibleTokens.states.isNotEmpty()) { "Cannot find token with identifier : $tokenIdentifier" }
            val tokenType = fungibleTokens.states.single().state.data.tokenType
            return subFlow(MoveNFTFlow(
                PartyAndToken(receivingParty, tokenType),
                observers,
                queryCriteria
            ))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class MoveNFTFlow(
        private val partyAndToken: PartyAndToken,
        private val observers: List<Party> = emptyList(),
        private val queryCriteria: QueryCriteria? = null
    ) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object CONSTRUCTING_TX : ProgressTracker.Step("Constructing transaction")
            object TRANSFERRING_TOKENS : ProgressTracker.Step("Transferring Tokens")
        }

        override val progressTracker = ProgressTracker(
            CONSTRUCTING_TX, TRANSFERRING_TOKENS
        )

        @Suspendable
        override fun call(): SignedTransaction {

            progressTracker.currentStep = CONSTRUCTING_TX

            val transactionBuilder = TransactionBuilder(getPreferredNotaryForToken(partyAndToken.token))

            progressTracker.currentStep = TRANSFERRING_TOKENS
            addMoveNonFungibleTokens(transactionBuilder, serviceHub, partyAndToken, queryCriteria)

            val observerSessions = sessionsForParties(observers)
            val participantSessions = sessionsForParties(listOf(partyAndToken.party))


            // Create new participantSessions if this is started as a top level flow.
            val signedTransaction = subFlow(
                ObserverAwareFinalityFlow(
                    transactionBuilder = transactionBuilder,
                    allSessions = participantSessions + observerSessions
                )
            )

            // Update the distribution list.
            subFlow(UpdateDistributionListFlow(signedTransaction))
            // Return the newly created transaction.
            return signedTransaction
        }

    }

    @InitiatedBy(MoveNFTFlow::class)
    class MoveNFTFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() = subFlow(MoveTokensFlowHandler(counterPartySession))

    }


}