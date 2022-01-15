package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import com.r3.demo.crossnotaryswap.contracts.LockContract
import com.r3.demo.crossnotaryswap.flows.utils.addMoveTokens
import com.r3.demo.crossnotaryswap.flows.utils.getRequestById
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.generic.argFail
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class UnlockEncumberedTokensFlow(
    private val encumberedTxHash: SecureHash,
    private val notarySignatureOnBuyerAssetTransfer: TransactionSignature,
    private val sellerSession: FlowSession
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val encumberedTransaction = serviceHub.validatedTransactions.getTransaction(encumberedTxHash)
            ?: argFail("Unable to fetch the transaction containing Lock state encumbrances with id $encumberedTxHash")

        // get the lock state
        val lockState = encumberedTransaction.coreTransaction
            .outRefsOfType<LockState>().single()

        // get the encumbered tokens owned by the composite key
        val tokensWithEncumbranceOwnedByComposite = encumberedTransaction.coreTransaction
            .outRefsOfType<AbstractToken>()

        // make it ours
        val tokensWithNewHolder = tokensWithEncumbranceOwnedByComposite
            .map { it.state.data.withNewHolder(ourIdentity) }

        // build transaction & verify the tx
        val transactionBuilder = TransactionBuilder(encumberedTransaction.notary!!)
        val constructedUnlockTx = transactionBuilder.apply {
            addMoveTokens(inputs = tokensWithEncumbranceOwnedByComposite,
                outputs = tokensWithNewHolder,
                additionalKeys = emptyList()
            )
            addInputState(lockState)
            // add lock release command with the notary signature on buyer asset transfer and add ourself as the signer
            addCommand(LockContract.Release(notarySignatureOnBuyerAssetTransfer), ourIdentity.owningKey)
        }
        constructedUnlockTx.verify(serviceHub)

        // sign the tx
        val selfSignedUnlockTx = serviceHub.signInitialTransaction(constructedUnlockTx)

        // send it to other participants
        val otherParticipantSessions =
            sessionsForParties(lockState.state.data.participants - ourIdentity - sellerSession.counterparty)

        return subFlow(FinalityFlow(transaction = selfSignedUnlockTx,
            sessions = otherParticipantSessions + sellerSession,
            statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}

class UnlockEncumberedTokensFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction =
        subFlow(ReceiveFinalityFlow(counterPartySession, statesToRecord = StatesToRecord.ALL_VISIBLE))
}

@InitiatingFlow
class UnlockEncumberedTokens(
    private val requestId: String,
    private val encumberedTxHash: SecureHash,
    private val notarySignatureOnBuyerAssetTransfer: TransactionSignature
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val exchangeRequestDTO = getRequestById(requestId)
        val sellerSession = initiateFlow(exchangeRequestDTO.seller)
        return subFlow(UnlockEncumberedTokensFlow(encumberedTxHash, notarySignatureOnBuyerAssetTransfer, sellerSession))
    }
}

@InitiatedBy(UnlockEncumberedTokens::class)
class UnlockEncumberedTokensHandler(private val counterPartySession: FlowSession): FlowLogic<SignedTransaction>(){
    @Suspendable
    override fun call(): SignedTransaction = subFlow(UnlockEncumberedTokensFlowHandler(counterPartySession))
}