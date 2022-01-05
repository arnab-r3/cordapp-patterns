package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.demo.generic.getPreferredNotaryForToken
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


class IssueTokensFlowWithNotarySelection
constructor(
    private val tokensToIssue: List<AbstractToken>,
    private val participantSessions: List<FlowSession>,
    private val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {

    /** Issue a single [FungibleToken]. */
    constructor(
        token: FungibleToken,
        participantSessions: List<FlowSession>,
        observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [FungibleToken] to self with no observers. */
    constructor(token: FungibleToken) : this(listOf(token), emptyList(), emptyList())

    /** Issue a single [NonFungibleToken]. */
    constructor(
        token: NonFungibleToken,
        participantSessions: List<FlowSession>,
        observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [NonFungibleToken] to self with no observers. */
    constructor(token: NonFungibleToken) : this(listOf(token), emptyList(), emptyList())

    @Suspendable
    override fun call(): SignedTransaction {

        val tokenTypes = tokensToIssue.map { it.tokenType }.toSet()
        require(tokenTypes.size == 1) {
            "All tokens must be of the same type"
        }
        val tokenType = tokenTypes.single()

        // Initialise the transaction builder with a preferred notary or choose a random notary.
        val transactionBuilder = TransactionBuilder(notary = getPreferredNotaryForToken(tokenType.tokenIdentifier))

        // Add all the specified tokensToIssue to the transaction. The correct commands and signing keys are also added.
        addIssueTokens(transactionBuilder, tokensToIssue)
        addTokenTypeJar(tokensToIssue, transactionBuilder)
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

class IssueTokensFlowWithNotarySelectionHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
}
