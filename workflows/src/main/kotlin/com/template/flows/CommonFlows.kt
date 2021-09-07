package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@InitiatingFlow
class CollectSignaturesAndFinalizeTransactionFlow(
    private val signedTransaction: SignedTransaction,
    private val myOptionalKeys: Iterable<PublicKey>?,
    private val signers: Set<Party>,
    private val participants: Set<Party>
) : FlowLogic<SignedTransaction>() {

    companion object {
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting Signatures") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(COLLECTING_SIGNATURES, FINALISING)
    }


    @Suspendable
    override fun call(): SignedTransaction {


        //TODO validate if the transaction command contains the list of signers


        // note the mechanism needs to be revisited, if there are a lot of signers to be included,
        // opening a large number of sessions can be detrimental to the performance and the memory usage of the app

        val signerSessions = (signers - ourIdentity).map { initiateFlow(it) }

        signerSessions.map { it.send(true) }

        // tell others they are just receiving
        val otherNonSigners = participants - signers - ourIdentity
        val otherNonSignerSessions = otherNonSigners.map { initiateFlow(it) }
        otherNonSignerSessions.map { it.send(false) }

        val signedTransactionFromParties =
            subFlow(CollectSignaturesFlow(
                signedTransaction,
                signerSessions,
                myOptionalKeys,
                COLLECTING_SIGNATURES.childProgressTracker()))

        val sessionsToReceiveTx = otherNonSignerSessions + signerSessions

        return subFlow(FinalityFlow(signedTransactionFromParties, sessionsToReceiveTx, FINALISING.childProgressTracker()))
    }

}

@InitiatedBy(CollectSignaturesAndFinalizeTransactionFlow::class)
class ResponderSignatureAndFinalityFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    fun hygieneCheckSignedTransaction(stx: SignedTransaction) {
        require(ourIdentity.owningKey in stx.requiredSigningKeys)
        { "${ourIdentity.name} is not a signing member of transaction : ${stx.id}" }

        require(session.counterparty.owningKey in stx.sigs.map { it.by })
        { "Transaction should be signed by the sender" }

    }

    @Suspendable
    override fun call(): SignedTransaction {
        val needsToSignTransaction = session.receive<Boolean>().unwrap { it }
        // only sign if instructed to do so
        if (needsToSignTransaction) {
            subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    hygieneCheckSignedTransaction(stx)
                    // TODO additional checks

                }
            })
        }
        // always save the transaction
        return subFlow(ReceiveFinalityFlow(otherSideSession = session))
    }
}


