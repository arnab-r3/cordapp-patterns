package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import com.r3.demo.crossnotaryswap.contracts.LockContract
import com.r3.demo.crossnotaryswap.flows.utils.addMoveTokens
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.generic.argFail
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class RevertEncumberedTokensFlow(
    private val encumberedTxHash: SecureHash,
    private val buyerSession: FlowSession
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val encumberedTx = serviceHub.validatedTransactions.getTransaction(encumberedTxHash)
            ?: argFail("Unable to find seller encumbered transaction with transaction id: $encumberedTxHash")


        val lockState = encumberedTx.coreTransaction.outRefsOfType<LockState>().single()
        val encumberedTxIssuer = lockState.state.data.creator

        val tokenStates = encumberedTx.coreTransaction.outRefsOfType<AbstractToken>()
            .filter {
                ourIdentity == serviceHub.identityService.requireWellKnownPartyFromAnonymous(it.state.data.holder)
                        && it.state.encumbrance != null
            }

        val outputStates = tokenStates.map { it.state.data.withNewHolder(encumberedTxIssuer) }

        val txBuilder = TransactionBuilder(notary = encumberedTx.notary)
            .addMoveTokens(inputs = tokenStates, outputs = outputStates, additionalKeys = emptyList())
            .addInputState(lockState)
            .addCommand(LockContract.Revert(), ourIdentity.owningKey)

        txBuilder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(txBuilder)

        val participantSessions =
            sessionsForParties(lockState.state.data.participants - ourIdentity - buyerSession.counterparty)

        return subFlow(FinalityFlow(
            transaction = selfSignedTx,
            sessions = participantSessions + buyerSession,
            statesToRecord = StatesToRecord.ALL_VISIBLE
        ))
    }
}

class RevertEncumberedTokensFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(counterPartySession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}