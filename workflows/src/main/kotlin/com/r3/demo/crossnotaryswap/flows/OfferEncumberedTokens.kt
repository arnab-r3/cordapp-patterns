package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.utils.addMoveToken
import com.r3.demo.crossnotaryswap.flows.utils.addMoveTokens
import com.r3.demo.crossnotaryswap.flows.utils.registerCompositeKey
import com.r3.demo.crossnotaryswap.flows.utils.verifySharedTransactionAgainstExchangeRequest
import com.r3.demo.crossnotaryswap.services.ExchangeRequestService
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getPreferredNotaryForToken
import com.template.flows.CollectSignaturesForComposites
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.CompositeKey
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

        // create the composite key to transfer the seller asset to. This is to enable equal control of both the
        // buyer and seller of the asset.
        val compositeKeyToTransferSellerAsset = serviceHub.registerCompositeKey(ourIdentity, buyerParty)
        val compositeKeyHolderParty = AnonymousParty(compositeKeyToTransferSellerAsset)

        // create the lock state that will encumber all tokens/states that the seller will transfer
        val lockState = LockState(validatedDraftTransferOfOwnership, ourIdentity, buyerParty)

        //prepare the transaction
        val transactionBuilder =
            TransactionBuilder(notary = getPreferredNotaryForToken(exchangeRequestDTO.sellerAssetRequest.tokenType))

        // construct the transaction that encumbers all states on the lock state based on the exchange request
        val encumberedTransaction =
            addEncumberedTokensForSeller(transactionBuilder, exchangeRequestDTO, lockState, compositeKeyHolderParty)

        // verify and sign
        encumberedTransaction.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(encumberedTransaction)
        val signedTx = subFlow(
            CollectSignaturesForComposites(
                selfSignedTx,
                listOf(exchangeRequestDTO.buyer as Party)
            ))

        // finalize
        // TODO add observer aware finality flow in the future
        val sessions = listOf(initiateFlow(exchangeRequestDTO.buyer))
        return subFlow(FinalityFlow(
            transaction = signedTx,
            sessions = sessions
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
            when {
                exchangeRequestDTO.sellerAssetRequest.tokenType.isPointer() -> {
                    addMoveToken(
                        serviceHub = serviceHub,
                        tokenIdentifier = exchangeRequestDTO.sellerAssetRequest.tokenType.tokenIdentifier,
                        tokenClass = uncheckedCast(exchangeRequestDTO.sellerAssetRequest.tokenType.tokenClass),
                        holder = compositeKeyHolderParty,
                        additionalKeys = listOf(exchangeRequestDTO.buyer.owningKey),
                        lockState = lockState
                    )
                }
                exchangeRequestDTO.sellerAssetRequest.tokenType.isRegularTokenType() -> {
                    addMoveTokens(serviceHub = serviceHub,
                        amount = uncheckedCast(exchangeRequestDTO.sellerAssetRequest.amount),
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
@InitiatedBy(OfferEncumberedTokens::class)
class OfferEncumberedTokensFlowHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        serviceHub.registerCompositeKey(ourIdentity, counterPartySession.counterparty)
        val signedEncumberedTx = subFlow(ReceiveFinalityFlow(otherSideSession = counterPartySession,
            statesToRecord = StatesToRecord.ALL_VISIBLE))

        val toLedgerTransaction = signedEncumberedTx.toLedgerTransaction(serviceHub)
        val lockState = toLedgerTransaction.outputsOfType(LockState::class.java).single()

        // execute the rest of the unlocking process for the recipient of the lock state, i.e. buyer
        if (ourIdentity == lockState.receiver) {
            val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)
            val exchangeRequestDTO = exchangeService.getExchangeRequestByTxId(lockState.txHash.toString())

            // verify if the transaction is ok as per the shared exchange request
            verifySharedTransactionAgainstExchangeRequest(exchangeRequestDTO.sellerAssetRequest, signedEncumberedTx.tx)

            val unsignedWireTransaction =
                exchangeRequestDTO.unsignedWireTransaction ?: flowFail("Exchange request should contain the " +
                        "unsigned wire transaction we constructed and shared earlier, failing since it does not!")

            // TODO trigger unlock encumbered of tokens
            // start by confirming the unsigned wire transaction
            val signedBuyerAssetTransferTx = subFlow(SignAndFinalizeTransferOfOwnership(unsignedWireTransaction))

            // get the signature of the notary
            val notarySignature = signedBuyerAssetTransferTx
                .sigs
                .single { it.by == lockState.controllingNotary.owningKey }

            subFlow(UnlockEncumberedTokensFlow(signedEncumberedTx.id, notarySignature))
        }

    }

}