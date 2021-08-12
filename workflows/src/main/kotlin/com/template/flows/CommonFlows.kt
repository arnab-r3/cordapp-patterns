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
    private val ss: SignedTransaction,
    override val progressTracker: ProgressTracker,
    val myOptionalKeys: Iterable<PublicKey>?,
    private val signers: Set<Party>,
    private val participants: Set<Party>
) : FlowLogic<SignedTransaction>() {

    val notary = serviceHub.networkMapCache.notaryIdentities[0]

    @Suspendable
    override fun call(): SignedTransaction {
        val signerSessions = signers.map { initiateFlow(it) }
        signerSessions.map{ it.send(true)}

        // tell others they are just receiving
        val otherNonSigners = participants - signers
        val otherNonSignerSessions = otherNonSigners.map{initiateFlow(it)}
        otherNonSignerSessions.map{it.send(false)}

        val signedTransactionFromParties = subFlow(CollectSignaturesFlow(ss, signerSessions, progressTracker))

        val sessionsToReceiveTx = otherNonSignerSessions + signerSessions

        // TODO autocomplete does not work here
        //servi << press ctrl+space here and nothing happens

        return subFlow(FinalityFlow(signedTransactionFromParties, sessionsToReceiveTx))
    }

}

@InitiatedBy(CollectSignaturesAndFinalizeTransactionFlow::class)
class ResponderSignatureAndFinalityFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {
        val needsToSignTransaction = session.receive<Boolean>().unwrap { it }
        // only sign if instructed to do so
        if (needsToSignTransaction) {
            subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {

                    require (ourIdentity.owningKey in stx.requiredSigningKeys)
                    {"${ourIdentity.name} is not a signing member of transaction : ${stx.id}"}

                    require(session.counterparty.owningKey in stx.sigs.map{it.by})
                    {"Transaction should be signed by the sender"}


                    // TODO autocomplete does not work here
                    //servi                     //<< press ctrl+space here and nothing happens
                    // val state = Template     // TemplateState exists inside contract, but no autocomplete :(
                }
            })
        }
        // always save the transaction
        return subFlow(ReceiveFinalityFlow(otherSideSession = session))
    }
}