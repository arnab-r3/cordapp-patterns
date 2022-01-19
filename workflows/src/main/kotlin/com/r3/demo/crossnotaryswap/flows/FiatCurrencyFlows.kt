package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import com.r3.demo.crossnotaryswap.flows.utils.TokenRegistry
import com.r3.demo.generic.getPreferredNotaryForToken
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.math.BigDecimal

object CurrencyFlows {

    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class IssueFiatCurrencyFlow(
        private val amount: BigDecimal,
        private val currency: String,
        private val receiver: Party? = null,
        private val observers: List<Party> = emptyList()
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            val currencyTokenType = TokenRegistry.getInstance(currency)
            val tokenToIssue = amount of currencyTokenType issuedBy ourIdentity heldBy (receiver ?: ourIdentity)

            return subFlow(
                IssueTokensFlowWithNotarySelection(
                    token = tokenToIssue,
                    participantSessions = sessionsForParticipants(listOf(tokenToIssue)),
                    observerSessions = sessionsForParties(observers))
            )
        }
    }


    @InitiatedBy(IssueFiatCurrencyFlow::class)
    class IssueTokensFlowHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            return subFlow(IssueTokensFlowWithNotarySelectionHandler(otherPartySession))
        }
    }


    @InitiatingFlow
    @StartableByRPC
    class GetBalanceFlow(private val currency: String) : FlowLogic<Amount<TokenType>>() {

        @Suspendable
        override fun call(): Amount<TokenType> {
            val currencyType = TokenRegistry.getInstance(currency)
            return serviceHub.vaultService.tokenBalance(currencyType)
        }
    }


    @InitiatingFlow
    @StartableByRPC
    class MoveFiatTokensFlow(
        private val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        private val observers: List<Party> = emptyList(),
        private val changeHolder: AbstractParty? = null
    ) : FlowLogic<SignedTransaction>() {

        constructor(
            currency: String,
            amount: Long,
            receiver: Party,
            changeHolder: AbstractParty? = null,
            observers: List<Party>
        ) : this(
            partiesAndAmounts = listOf(PartyAndAmount(receiver, amount of TokenRegistry.getInstance(currency))),
            observers = observers,
            changeHolder = changeHolder
        )

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

            val tokenTypes = partiesAndAmounts.map { it.amount.token }.toSet()

            require(tokenTypes.size == 1) { "All tokens must be of the same type" }

            // Initialise the transaction builder with no notary.
            val transactionBuilder = TransactionBuilder(getPreferredNotaryForToken(tokenTypes.single()))
            // Add all the specified inputs and outputs to the transaction.
            // The correct commands and signing keys are also added.

            progressTracker.currentStep = TRANSFERRING_TOKENS
            addMoveFungibleTokens(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                partiesAndAmounts = partiesAndAmounts,
                changeHolder = changeHolder ?: ourIdentity,
                queryCriteria = null
            )

            val observerSessions = sessionsForParties(observers)
            val participantSessions = sessionsForParties(partiesAndAmounts.map { it.party })


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

    @InitiatedBy(MoveFiatTokensFlow::class)
    class MoveFiatTokensFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            return subFlow(MoveFungibleTokensHandler(counterPartySession))
        }

    }

}





