package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import com.r3.demo.crossnotaryswap.utils.CurrencyUtils
import com.r3.demo.generic.getPreferredNotaryForToken
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object CurrencyFlows {

    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class IssueFiatCurrencyFlow(
        private val amount: Long,
        private val currency: String,
        private val receiver: Party? = null
    ) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object GETTING_IDENTITIES : ProgressTracker.Step("Getting ours and recipients identity")
            object SETTING_NOTARY : ProgressTracker.Step("Setting the preferred notary for the token")
            object PARSING_CURRENCY : ProgressTracker.Step("Parsing targetCurrency to issue")
            object ISSUING_TOKENS : ProgressTracker.Step("Issuing tokens to recipient")
        }

        override val progressTracker = ProgressTracker(
            GETTING_IDENTITIES,
            SETTING_NOTARY,
            PARSING_CURRENCY,
            ISSUING_TOKENS
        )

        @Suspendable
        override fun call(): SignedTransaction {


            progressTracker.currentStep = PARSING_CURRENCY
            val currencyTokenType = CurrencyUtils.getInstance(currency)

            val amountOfType = Amount(amount, currencyTokenType)

            progressTracker.currentStep = GETTING_IDENTITIES
            val tokenToIssue = amountOfType issuedBy ourIdentity heldBy (receiver ?: ourIdentity)

            val tokensToIssue = listOf(tokenToIssue)

            progressTracker.currentStep = SETTING_NOTARY
            // Initialise the transaction builder with a preferred notary or choose a random notary.
            val transactionBuilder = TransactionBuilder(notary = getPreferredNotaryForToken(serviceHub, currency))
            // Add all the specified tokensToIssue to the transaction. The correct commands and signing keys are also added.
            addIssueTokens(transactionBuilder, tokensToIssue)
            addTokenTypeJar(tokensToIssue, transactionBuilder)

            val observerSessions = sessionsForParties(emptyList())
            val participantSessions = sessionsForParticipants(tokensToIssue)

            progressTracker.currentStep = ISSUING_TOKENS
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


    @InitiatedBy(IssueTokensFlow::class)
    class IssueTokensFlowHandler(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(otherPartySession))
        }
    }


    @InitiatingFlow
    @StartableByRPC
    class GetBalanceFlow(private val currency: String) : FlowLogic<Amount<TokenType>>() {

        @Suspendable
        override fun call(): Amount<TokenType> {
            val currencyType = CurrencyUtils.getInstance(currency)
            return serviceHub.vaultService.tokenBalance(currencyType)
        }
    }


    @InitiatingFlow
    @StartableByRPC
    class MoveTokensFlow(private val currency: String,
                         private val amount: Long,
                         private val receiver: Party) : FlowLogic<SignedTransaction>() {


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
            val currencyTokenType = CurrencyUtils.getInstance(currency)
            val amountOfCurrency = Amount(amount, currencyTokenType)

            val partyAndAmount = PartyAndAmount(receiver, amountOfCurrency)

            progressTracker.currentStep = TRANSFERRING_TOKENS
            //return subFlow(MoveFungibleTokens(partyAndAmount, emptyList()))

            // Initialise the transaction builder with no notary.
            val transactionBuilder = TransactionBuilder(getPreferredNotaryForToken(serviceHub, currency))
            // Add all the specified inputs and outputs to the transaction.
            // The correct commands and signing keys are also added.

            val partiesAndAmounts = listOf(PartyAndAmount(receiver, amountOfCurrency))

            addMoveFungibleTokens(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                partiesAndAmounts = partiesAndAmounts,
                changeHolder = ourIdentity,
                queryCriteria = null
            )

            val observerSessions = sessionsForParties(emptyList())
            val participantSessions = sessionsForParties(listOf(receiver))


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

    @InitiatedBy(MoveTokensFlow::class)
    class MoveTokensHandler(private val counterPartySession: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            return subFlow(MoveFungibleTokensHandler(counterPartySession))
        }

    }

}





