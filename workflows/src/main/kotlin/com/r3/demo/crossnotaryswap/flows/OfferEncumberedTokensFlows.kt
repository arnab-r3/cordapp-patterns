package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.*
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getPreferredNotaryForToken
import com.template.flows.CollectSignaturesForComposites
import com.template.flows.CollectSignaturesForCompositesHandler
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
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
class OfferEncumberedTokensFlow(
    private val exchangeRequestDTO: ExchangeRequestDTO,
    private val validatedDraftTransferOfOwnership: ValidatedDraftTransferOfOwnership,
    private val buyerSession: FlowSession
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val buyerParty = exchangeRequestDTO.buyer.toParty(serviceHub)

        // create the composite key to transfer the seller asset to. This is to enable equal control of both the
        // buyer and seller of the asset.
        val compositeKeyToTransferSellerAsset = serviceHub.registerCompositeKey(ourIdentity, buyerParty)
        val compositeKeyHolderParty = AnonymousParty(compositeKeyToTransferSellerAsset)

        // create the lock state that will encumber all tokens/states that the seller will transfer
        val lockState = LockState(validatedDraftTransferOfOwnership, ourIdentity, buyerParty)

        val tokenTypeForSeller = getTokenTypeFromAssetRequest(exchangeRequestDTO.sellerAssetRequest)
        //prepare the transaction
        val transactionBuilder =
            TransactionBuilder(notary = getPreferredNotaryForToken(tokenTypeForSeller))

        // construct the transaction that encumbers all states on the lock state based on the exchange request
        val encumberedTransaction =
            addEncumberedTokensForSeller(transactionBuilder, exchangeRequestDTO, lockState, compositeKeyHolderParty)

        // verify and sign
        encumberedTransaction.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(encumberedTransaction)
        val signedTx = subFlow(
            CollectSignaturesForComposites(
                selfSignedTx,
                setOf(buyerSession)
            ))

        return subFlow(FinalityFlow(
            transaction = signedTx,
            sessions = listOf(buyerSession)
        ))
    }

    /**
     * Add the encumbered tokens to the transaction builder based in [ExchangeRequestDTO]
     * @param transactionBuilder to add the tokens to
     * @param exchangeRequestDTO having information about the swap
     * @param lockState to be used
     * @param compositeKeyHolderParty having a [CompositeKey] of both the seller and the buyer with equal weights
     */
    @Suspendable
    private fun addEncumberedTokensForSeller(
        transactionBuilder: TransactionBuilder,
        exchangeRequestDTO: ExchangeRequestDTO,
        lockState: LockState,
        compositeKeyHolderParty: AnonymousParty
    ): TransactionBuilder {
        return with(transactionBuilder) {
            when (exchangeRequestDTO.sellerAssetRequest) {
                is NonFungibleAssetRequest -> {
                    addMoveToken(
                        serviceHub = serviceHub,
                        tokenIdentifier = exchangeRequestDTO.sellerAssetRequest.tokenIdentifier.toString(),
                        holder = compositeKeyHolderParty,
                        additionalKeys = listOf(exchangeRequestDTO.buyer.owningKey),
                        lockState = lockState
                    )
                }
                is FungibleAssetRequest -> {
                    addMoveTokens(serviceHub = serviceHub,
                        amount = exchangeRequestDTO.sellerAssetRequest.tokenAmount,
                        holder = compositeKeyHolderParty,
                        changeHolder = ourIdentity,
                        additionalKeys = listOf(exchangeRequestDTO.buyer.owningKey),
                        lockState = lockState
                    )
                }
                else -> flowFail("Unable to determine token type for seller. " +
                        "Offering encumbered tokens for custom token type is not supported")
            }
            // add additional 30s buffer to the time window to consume the lock state in addition
            // to the originally set time window in the unsigned tx
            setTimeWindow(
                TimeWindow.untilOnly(validatedDraftTransferOfOwnership.tx.timeWindow?.untilTime!!
                    .plusSeconds(30))
            )
        }
    }
}

/**
 * Buyer's response to the offered encumbered tokens
 */
class OfferEncumberedTokensFlowHandler(private val counterPartySession: FlowSession) :
    FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        serviceHub.registerCompositeKey(ourIdentity, counterPartySession.counterparty)

        subFlow(CollectSignaturesForCompositesHandler(counterPartySession))

        val signedEncumberedTx = subFlow(ReceiveFinalityFlow(otherSideSession = counterPartySession,
            statesToRecord = StatesToRecord.ALL_VISIBLE))

        val toLedgerTransaction = signedEncumberedTx.toLedgerTransaction(serviceHub)
        val lockState = toLedgerTransaction.outputsOfType(LockState::class.java).single()

        // execute the rest of the unlocking process for the recipient of the lock state, i.e. buyer
        // TODO check identity later
        //if (ourIdentity == lockState.receiver) {
        val exchangeRequestDTO = getExchangeRequestByTxId(lockState.txIdWithNotaryMetadata.txId.toString())

        // verify if the transaction is ok as per the shared exchange request
        verifySharedTransactionAgainstExchangeRequest(exchangeRequestDTO.sellerAssetRequest, signedEncumberedTx.tx)

        return signedEncumberedTx
    }
}

@InitiatingFlow
class OfferEncumberedTokens(
    private val requestId: String,
    private val validatedDraftTransferOfOwnership: ValidatedDraftTransferOfOwnership
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val exchangeRequestDTO = getRequestById(requestId)
        val buyerSession = initiateFlow(exchangeRequestDTO.buyer)
        return subFlow(OfferEncumberedTokensFlow(exchangeRequestDTO, validatedDraftTransferOfOwnership, buyerSession))
    }
}

@InitiatedBy(OfferEncumberedTokens::class)
class OfferEncumberedTokensHandler(private val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction = subFlow(OfferEncumberedTokensFlowHandler(counterPartySession))
}
