package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.crossnotaryswap.flows.utils.getDeadlineFromLockState
import com.r3.demo.crossnotaryswap.flows.utils.getNotarySigFromEncumberedTx
import com.r3.demo.crossnotaryswap.flows.utils.getRequestById
import com.r3.demo.crossnotaryswap.types.RequestStatus
import com.r3.demo.generic.flowFail
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import java.time.Instant

object CrossNotarySwapDriverFlows {

    @CordaSerializable
    data class RevertNotification(val isReverted: Boolean = false)

    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class BuyerDriverFlow(
        private val requestId: String
    ) : FlowLogic<Unit>() {

        // store the seller session
        private lateinit var sellerSession: FlowSession

        @Suspendable
        override fun call() {
            val exchangeRequestDTO = getRequestById(requestId)
            if (exchangeRequestDTO.requestStatus != RequestStatus.APPROVED)
                flowFail("Provided request id $requestId is not Approved to initiate cross notary swap of assets, " +
                        "found status: ${exchangeRequestDTO.requestStatus}")

            // initiate the draft transfer of ownership
            sellerSession = initiateFlow(exchangeRequestDTO.seller)
            // send the draft transfer of ownership as a WireTransaction
            val unsignedWireTx = subFlow(DraftTransferOfOwnershipFlow(requestId, sellerSession))

            // receive the locked / encumbered tokens
            val signedEncumberedTx = subFlow(OfferEncumberedTokensFlowHandler(sellerSession))

            val reverted = sellerSession.receive<RevertNotification>().unwrap { it }.isReverted

            if (!reverted) {
                // send the promised buyer assets
                val signedBuyerAssetTransferTx = subFlow(
                    SignAndFinalizeTransferOfOwnershipFlow(unsignedWireTx, sellerSession)
                )
                // unlock the locked tokens
                val notarySigOnBuyerTransfer =
                    getNotarySigFromEncumberedTx(signedEncumberedTx, signedBuyerAssetTransferTx)

                subFlow(UnlockEncumberedTokensFlow(signedEncumberedTx.id, notarySigOnBuyerTransfer, sellerSession))

            } else {
                subFlow(RevertEncumberedTokensFlowHandler(sellerSession))
            }
        }
    }


    @InitiatedBy(BuyerDriverFlow::class)
    class SellerDriverFlow(private val buyerSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            // get the draft transfer of ownership from the responder flow as a wireTransaction
            val (requestId, validatedDraftTransferOfOwnership) =
                subFlow(DraftTransferOfOwnershipFlowHandler(buyerSession))

            // Construct and offer the encumbered tokens
            val exchangeRequestDto = getRequestById(requestId)

            val signedEncumberedTx =
                subFlow(OfferEncumberedTokensFlow(exchangeRequestDto, validatedDraftTransferOfOwnership, buyerSession))

            // check if deadline has passed
            val deadline = getDeadlineFromLockState(signedEncumberedTx)
            if (Instant.now().isBefore(deadline)) {

                buyerSession.send(RevertNotification(false))

                // receive the buyer assets
                subFlow(SignAndFinalizeTransferOfOwnershipFlowHandler(buyerSession))

                // receive the unlocked tokens
                subFlow(UnlockEncumberedTokensFlowHandler(buyerSession))
            } else {
                // deadline has passed
                buyerSession.send(RevertNotification(true))
                subFlow(RevertEncumberedTokensFlow(signedEncumberedTx.id, buyerSession))
            }

        }

    }

}