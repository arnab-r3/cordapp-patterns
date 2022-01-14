package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * Collect signatures for the provided [SignedTransaction], from the list of [Party] provided.
 * This is an initiating flow, and is used where some required signatures are from [CompositeKey]s.
 * The standard Corda CollectSignaturesFlow will not work in this case.
 * @param stx - the [SignedTransaction] to sign
 * @param signers - the list of signing [Party]s
 */
internal class CollectSignaturesForComposites(
    private val stx: SignedTransaction,
    private val signerSessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        // We filter out any responses that are not TransactionSignature`s (i.e. refusals to sign).
        val signatures = signerSessions
            .map { it.sendAndReceive<Any>(stx).unwrap { data -> data } }
            .filterIsInstance<TransactionSignature>()
        return stx.withAdditionalSignatures(signatures)
    }
}

/**
 * Responder flow for [CollectSignaturesForComposites] flow.
 */
internal class CollectSignaturesForCompositesHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        otherPartySession.receive<SignedTransaction>().unwrap { partStx ->
            // TODO: add conditions where we might not sign

            val returnStatus = serviceHub.createSignature(partStx)
            otherPartySession.send(returnStatus)
        }
    }
}