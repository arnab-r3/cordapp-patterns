package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction

/**
 * Sign and finalise the unsigned art transfer [WireTransaction] and return the [SignedTransaction].
 * @property wireTransaction the unsigned transaction to sign and finalise.
 * @return the transfer of ownership transaction, signed and finalised.
 */
class SignAndFinalizeTransferOfOwnership(
    private val wireTransaction: WireTransaction,
    private val sellerSession: FlowSession
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        wireTransaction.toLedgerTransaction(serviceHub).verify()

        val tx = wireTransaction.toTransactionBuilder()
        val selfSignedTx = serviceHub.signInitialTransaction(tx, ourIdentity.owningKey)

        // get participants of the transaction apart from the buyer and the seller
        val additionalParticipants = tx.outputStates().flatMap { it.data.participants }
            .distinct()
            .mapNotNull {
                serviceHub.identityService.wellKnownPartyFromAnonymous(it)
            }
            .filter { it !in listOf(ourIdentity, sellerSession.counterparty) }

        // get the signers of the transaction
        val otherSigners = tx.commands().flatMap { it.signers }
            .distinct()
            .mapNotNull {
                serviceHub.identityService.partyFromKey(it)
            }
            .filter { it !in listOf(ourIdentity, sellerSession.counterparty) }

        // collect signatures
        val signerSessions = otherSigners.map { initiateFlow(it) }
        val signedTx = subFlow(
            CollectSignaturesFlow(
                selfSignedTx,
                signerSessions + sellerSession
            )
        )
        //finalize transaction
        val participantSessions = additionalParticipants.map { initiateFlow(it) }
        return subFlow(FinalityFlow(signedTx, participantSessions + sellerSession))
    }

    /**
     * Convert this [WireTransaction] into a [TransactionBuilder] instance.
     * @return [TransactionBuilder] instance.
     */
    @Suspendable
    private fun WireTransaction.toTransactionBuilder(): TransactionBuilder {
        return TransactionBuilder(
            notary = this.notary!!,
            inputs = this.inputs.toMutableList(),
            attachments = this.attachments.toMutableList(),
            outputs = this.outputs.toMutableList(),
            commands = this.commands.toMutableList(),
            window = this.timeWindow,
            privacySalt = this.privacySalt,
            references = this.references.toMutableList(),
            serviceHub = serviceHub
        )
    }
}

/**
 * Responder flow for [SignAndFinalizeTransferOfOwnership].
 * Sign and finalise the art transfer transaction.
 */
class SignAndFinaliseTxForPushHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSession))
        }
    }
}