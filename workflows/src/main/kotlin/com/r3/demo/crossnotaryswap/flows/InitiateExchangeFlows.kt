package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.crossnotaryswap.flows.dto.AbstractAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.services.ExchangeRequestService
import com.r3.demo.crossnotaryswap.types.RequestStatus
import com.r3.demo.generic.flowFail
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.unwrap


/**
 * Flows that finalises the exchange of assets between two parties
 */
object InitiateExchangeFlows {

    /**
     * The buyer initiates the proposal with the details of the requested asset from the seller (viz. Token Identifier
     * and optional amount) and provides his asset details within the buyerTokenIdentifier and the buyerTokenAmount
     * fields.
     * @param sellerParty to send the exchange request to
     * @param sellerAssetRequest to ask
     * @param buyerAssetRequest to propose
     */
    @StartableByRPC
    @InitiatingFlow
    class ExchangeRequesterFlow(
        private val sellerParty: AbstractParty,
        private val sellerAssetRequest: AbstractAssetRequest,
        private val buyerAssetRequest: AbstractAssetRequest
    ) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {

            val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)

            if (!exchangeService.isExchangeAssetOwned(buyerAssetRequest, ourIdentity))
                flowFail("The specified asset does not belong to us " +
                        "or is in insufficient quantity: $buyerAssetRequest")

            val exchangeRequestDto = ExchangeRequestDTO(
                buyer = ourIdentity,
                seller = sellerParty,
                sellerAssetRequest = sellerAssetRequest,
                buyerAssetRequest = buyerAssetRequest
            )

            exchangeService.newExchangeRequestFromDto(exchangeRequestDto)
            val sellerSession = initiateFlow(sellerParty)
            sellerSession.send(exchangeRequestDto)

            logger.info("Returning exchange request id: ${exchangeRequestDto.requestId.toString()}")
            return exchangeRequestDto.requestId.toString()
        }

    }

    /**
     * The request from the buyer is saved on the seller side
     */
    @InitiatedBy(ExchangeRequesterFlow::class)
    class ExchangeRequesterHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)
            // receive the request and save it
            val exchangeRequestDto = counterPartySession.receive<ExchangeRequestDTO>().unwrap { it }
            exchangeService.newExchangeRequestFromDto(exchangeRequestDto)
        }
    }


    /**
     * A decision is made on the request
     * @param requestId on which the decision will be made
     * @param approved flag denoting whether the request is approved
     * @param rejectionReason is optional denoting the rejection reason if the request is rejected
     */
    @InitiatingFlow
    @StartableByRPC
    class ExchangeResponderFlow(
        private val requestId: String,
        private val approved: Boolean,
        private val rejectionReason: String? = null) : FlowLogic<Unit>(){

        @Suspendable
        override fun call() {

            val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)
            val exchangeRequestDto = exchangeService.getRequestById(requestId)
            val counterPartySession = initiateFlow(exchangeRequestDto.buyer)

            // check if we have sufficient balance of the requested asset
            if (!exchangeService.isExchangeAssetOwned(exchangeRequestDto.sellerAssetRequest, ourIdentity)) {
                val reason = "The specified asset does not belong to us " +
                        "or is in insufficient quantity: ${exchangeRequestDto.sellerAssetRequest}"
                val rejectedRequest = exchangeRequestDto.deny(reason)

                counterPartySession.send(rejectedRequest)

                flowFail(reason)
            }

            // set the status appropriately on the dto and send the response
            val exchangeRequestResponseDto = if (approved) {
                exchangeService.setRequestStatus(exchangeRequestDto.requestId.toString(), RequestStatus.APPROVED)
                exchangeRequestDto.approve()
            }
            else {
                exchangeService.setRequestStatus(exchangeRequestDto.requestId.toString(), RequestStatus.DENIED, rejectionReason)
                exchangeRequestDto.deny(rejectionReason)
            }

            counterPartySession.send(exchangeRequestResponseDto)
        }
    }

    /**
     * Handler on the buyer side that receives the decision from the seller
     */
    @InitiatedBy(ExchangeResponderFlow::class)
    class ExchangeResponderFlowHandler(private val counterPartySession: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)

            val exchangeRequestResponse = counterPartySession.receive<ExchangeRequestDTO>().unwrap { it }

            val exchangeRequestDto = exchangeService.getRequestById(exchangeRequestResponse.requestId.toString())


            if (exchangeRequestDto != exchangeRequestResponse) {
                val reason =
                    "One or more attributes in the response has changed. Cannot proceed with this exchange request"
                exchangeService.setRequestStatus(exchangeRequestDto.requestId.toString(), RequestStatus.ABORTED, reason)
                flowFail(reason)
            }

            if (exchangeRequestResponse.requestStatus == null) {
                val reason = "The response from counterparty ${exchangeRequestResponse.seller} must contain the requestStatus"
                exchangeService.setRequestStatus(exchangeRequestDto.requestId.toString(), RequestStatus.ABORTED, reason)
                flowFail(reason)
            }

            exchangeService.setRequestStatus(exchangeRequestDto.requestId.toString(),
                exchangeRequestResponse.requestStatus, exchangeRequestDto.reason)

            subFlow(DraftTransferOfOwnershipFlow(exchangeRequestDto.requestId.toString()))
        }

    }

}