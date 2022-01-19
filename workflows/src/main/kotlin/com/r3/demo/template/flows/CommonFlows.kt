package com.r3.demo.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.generic.argFail
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@InitiatingFlow
class CollectSignaturesAndFinalizeTransactionFlow(
    private val builder: TransactionBuilder,
    private val myOptionalKeys: Iterable<PublicKey>? = null,
    private val signers: Set<Party>,
    private val participants: Set<Party>
) : FlowLogic<SignedTransaction>() {

    companion object {
        object SIGNING_TRANSACTION: ProgressTracker.Step("Self Signing Transaction")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting Signatures") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SIGNING_TRANSACTION, COLLECTING_SIGNATURES, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {


        val observerParties = participants - ourIdentity
        val signerParties = signers - ourIdentity

        builder.notary ?: argFail("Notary should not be absent in builder")

        require(observerParties.containsAll(signerParties)) {"Signers should be a subset or proper subset of participants"}

        //TODO validate if the transaction command contains the list of signers

        progressTracker.currentStep = SIGNING_TRANSACTION

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)

        val flowSessions = observerParties.map { counterParty -> initiateFlow(counterParty) }

        // communicate to others if they are required to sign the transaction
        flowSessions.forEach { session -> session.send(session.counterparty in signerParties) }

        // note the mechanism needs to be revisited, if there are a lot of signers to be included,
        // opening a large number of sessions can be detrimental to the performance and the memory usage of the app
        val signerSessions = flowSessions.filter { it.counterparty in signerParties }

        val signedTransactionFromParties =
            subFlow(CollectSignaturesFlow(
                selfSignedTransaction,
                signerSessions,
                myOptionalKeys,
                COLLECTING_SIGNATURES.childProgressTracker()))

        return subFlow(FinalityFlow(signedTransactionFromParties,
            flowSessions,
            FINALISING.childProgressTracker()))
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
        val stx = if (needsToSignTransaction) {
            val signedTransaction = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    hygieneCheckSignedTransaction(stx)
                    // TODO additional checks
                }
            }
            subFlow(signedTransaction)
        }else null
        // always save the transaction
        return subFlow(ReceiveFinalityFlow(
            otherSideSession = session,
            expectedTxId = stx?.id,
            statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}


