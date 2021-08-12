package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TemplateContract
import com.template.states.DealState
import com.template.states.ProductState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.math.BigDecimal
import java.util.*


@InitiatingFlow
@StartableByRPC
class DealInitiator(private val receiver: Party, private val productName: String, val price: BigDecimal) : FlowLogic<SignedTransaction>() {
    /**
     * This is where you fill out your business logic.
     */
    override fun call(): SignedTransaction {

        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val p = ProductState(UUID.randomUUID(), productName, price)
        val outputState = DealState(listOf(p), listOf(ourIdentity, receiver), UniqueIdentifier())
        val builder = TransactionBuilder(notary)
            .addCommand(TemplateContract.Commands.CreateDeal(), listOf(ourIdentity.owningKey, receiver.owningKey))
            .addOutputState(outputState)
        builder.verify(serviceHub)

        val ptx = serviceHub.signInitialTransaction(builder)

        val otherParties = outputState.participants.map { it as Party } - ourIdentity

        val partySessions = otherParties.map { initiateFlow(it) }

        val signedTransaction = subFlow(CollectSignaturesFlow(ptx, partySessions))

        return subFlow(FinalityFlow(signedTransaction, partySessions))

    }
}

@InitiatedBy(DealInitiator::class)
class DealResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    /**
     * This is where you fill out your business logic.
     */
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                //Addition checks
            }
        }
        val txId = subFlow(signTransactionFlow).id
        return subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = txId))
    }
}


