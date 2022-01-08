package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.utils.addMoveToken
import com.r3.demo.crossnotaryswap.flows.utils.addMoveTokens
import com.r3.demo.crossnotaryswap.flows.utils.registerCompositeKey
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getPreferredNotaryForToken
import com.template.flows.CollectSignaturesForComposites
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction


/**
 * Invoked by the **seller** party to transfer the seller assets and encumber them with a [LockState]
 * @param exchangeRequestDTO containing the asset exchange information
 * @param validatedDraftTransferOfOwnership object encapsulating information about the draft unsigned [WireTransaction],
 * the notary [SignatureMetadata]
 */
@InitiatingFlow
class OfferEncumberedTokens(
    private val exchangeRequestDTO: ExchangeRequestDTO,
    private val validatedDraftTransferOfOwnership: ValidatedDraftTransferOfOwnership
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val buyerParty = exchangeRequestDTO.buyer.toParty(serviceHub)
        val compositeKeyToTransferSellerAsset = serviceHub.registerCompositeKey(ourIdentity, buyerParty)
        val compositeKeyHolderParty = AnonymousParty(compositeKeyToTransferSellerAsset)

        val lockState = LockState(validatedDraftTransferOfOwnership, ourIdentity, buyerParty)

        val transactionBuilder =
            TransactionBuilder(notary = getPreferredNotaryForToken(exchangeRequestDTO.sellerAsset.tokenType))

        val encumberedTransaction = with(transactionBuilder) {
            when {
                exchangeRequestDTO.sellerAsset.tokenType.isPointer() -> {
                    addMoveToken(
                        serviceHub = serviceHub,
                        tokenIdentifier = exchangeRequestDTO.sellerAsset.tokenType.tokenIdentifier,
                        tokenClass = uncheckedCast(exchangeRequestDTO.sellerAsset.tokenType.tokenClass),
                        holder = compositeKeyHolderParty,
                        additionalKeys = listOf(exchangeRequestDTO.buyer.owningKey),
                        lockState = lockState
                    )
                }
                exchangeRequestDTO.sellerAsset.tokenType.isRegularTokenType() -> {
                    addMoveTokens(serviceHub = serviceHub,
                        amount = uncheckedCast(exchangeRequestDTO.sellerAsset.amount),
                        holder = compositeKeyHolderParty,
                        changeHolder = ourIdentity,
                        additionalKeys = listOf(exchangeRequestDTO.buyer.owningKey),
                        lockState = lockState
                    )

                }
                else -> flowFail("Unable to determine token type for seller. " +
                        "Offering encumbered tokens for custom token type is not supported")
            }
            // add additional 30s buffer to the timewindow to consume the lock state in addition to the originally set timewindow in the unsigned tx
            setTimeWindow(TimeWindow.untilOnly(validatedDraftTransferOfOwnership.tx.timeWindow?.untilTime!!.plusSeconds(
                30)))
        }

        encumberedTransaction.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(encumberedTransaction)

        val signedTx = subFlow(
            CollectSignaturesForComposites(
                selfSignedTx,
                listOf(exchangeRequestDTO.buyer as Party)
            ))

        val sessions = listOf(initiateFlow(exchangeRequestDTO.buyer))
        return subFlow(FinalityFlow(
            transaction = signedTx,
            sessions = sessions
        ))
    }
}

@InitiatedBy(OfferEncumberedTokens::class)
class OfferEncumberedTokensFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        serviceHub.registerCompositeKey(ourIdentity, counterPartySession.counterparty)
        subFlow(ReceiveFinalityFlow(otherSideSession = counterPartySession,
            statesToRecord = StatesToRecord.ALL_VISIBLE))
    }

}