package com.r3.demo.crossnotaryswap.services

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.types.RequestStatus
import com.r3.demo.generic.argFail
import com.r3.demo.generic.flowFail
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class ExchangeRequestService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    fun getRequestById(requestId: String): ExchangeRequestDTO =
        ExchangeRequestDTO.fromExchangeRequestEntity(getRequestEntityById(requestId))


    private fun getRequestEntityById(requestId: String): ExchangeRequest =
        appServiceHub.withEntityManager {
            this.find(ExchangeRequest::class.java, requestId)
                ?: argFail("Cannot find exchange request with Id: $requestId")
        }

    fun setRequestStatus(requestId: String, requestStatus: RequestStatus) {
        val request = getRequestEntityById(requestId)
        appServiceHub.withEntityManager {
            if (request.requestStatus == null && requestStatus != RequestStatus.REQUESTED) {
                flowFail("Status update should be REQUESTED for null requestStatus")
            } else if (request.requestStatus != null && request.requestStatus!! in listOf(RequestStatus.APPROVED,
                    RequestStatus.DENIED)
            ) {
                flowFail("Request status has already been set with the response")
            }
            request.requestStatus = requestStatus
            merge(request)
        }
    }

    fun setTxId(requestId: String, txId: String){
        val request = getRequestEntityById(requestId)
        appServiceHub.withEntityManager{
            if (request.requestStatus != null || request.requestStatus == RequestStatus.REQUESTED) {
                flowFail("Cannot set the transaction id for requests with no request status or REQUESTED request status")
            }else if(request.txId != null) {
                flowFail("Transaction id has already been set for request id $requestId")
            }
            request.txId = txId
            merge(request)
        }
    }

    fun newExchangeRequest(
        buyer: AbstractParty,
        seller: AbstractParty,
        buyerAsset: Amount<TokenType>,
        sellerAsset: Amount<TokenType>
    ): ExchangeRequestDTO {
        val exchangeRequestDTO = ExchangeRequestDTO(
            buyer = buyer,
            seller = seller,
            buyerAsset = buyerAsset,
            sellerAsset = sellerAsset,
            requestStatus = RequestStatus.REQUESTED
        )
        appServiceHub.withEntityManager{
            persist(exchangeRequestDTO.toExchangeRequestEntity())
        }
        return exchangeRequestDTO
    }


    fun newExchangeRequestFromDto(exchangeRequestDTO: ExchangeRequestDTO){
        val exchangeRequestEntity = exchangeRequestDTO.toExchangeRequestEntity()
        if (exchangeRequestDTO.requestStatus == null) {
            exchangeRequestEntity.requestStatus = RequestStatus.REQUESTED
        }
        appServiceHub.withEntityManager {
            persist(exchangeRequestEntity)
        }
    }
}