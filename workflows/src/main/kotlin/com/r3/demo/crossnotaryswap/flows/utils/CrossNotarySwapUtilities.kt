package com.r3.demo.crossnotaryswap.flows.utils

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.r3.corda.lib.tokens.workflows.utilities.rowsToAmount
import com.r3.corda.lib.tokens.workflows.utilities.sumTokenCriteria
import com.r3.demo.crossnotaryswap.flows.dto.AbstractAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.schemas.ExchangeRequest
import com.r3.demo.crossnotaryswap.services.ExchangeRequestService
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.types.RequestStatus
import com.r3.demo.generic.argFail
import com.r3.demo.generic.flowFail
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import java.time.Instant


/**
 * Get Exchange request by requestId
 */
@Suspendable
fun FlowLogic<*>.getRequestById(requestId: String): ExchangeRequestDTO =
    ExchangeRequestDTO.fromExchangeRequestEntity(getRequestEntityById(requestId, serviceHub))


/**
 * Get Exchange request entity by requestId
 */
fun getRequestEntityById(requestId: String, serviceHub: ServiceHub): ExchangeRequest =
    serviceHub.withEntityManager {
        this.find(ExchangeRequest::class.java, requestId)
            ?: argFail("Cannot find exchange request with Id: $requestId")
    }

/**
 * Set request status of the exchange request
 * @param requestId to be modified
 * @param requestStatus to be set for the [ExchangeRequest]
 */
@Suspendable
fun FlowLogic<*>.setRequestStatus(requestId: String, requestStatus: RequestStatus, reason: String? = null) {
    val request = getRequestEntityById(requestId, serviceHub)
    serviceHub.withEntityManager {
        if (request.requestStatus == null && requestStatus !in listOf(RequestStatus.REQUESTED,
                RequestStatus.ABORTED)
        ) {
            flowFail("Status update should be REQUESTED/ABORTED for null requestStatus")
        } else if (request.requestStatus == RequestStatus.REQUESTED && request.requestStatus!! in listOf(
                RequestStatus.APPROVED,
                RequestStatus.DENIED)
        ) {
            flowFail("Request status has already been set with the response")
        }
        request.requestStatus = requestStatus
        request.reason = reason
        merge(request)
    }
}

/**
 * Set the transaction id after completion of the request lifecycle
 * @param requestId to be modified
 * @param txId to be updated in the [ExchangeRequest]
 */
@Suspendable
fun FlowLogic<*>.setTxDetails(requestId: String, txId: String, tx: ByteArray) {
    val request = getRequestEntityById(requestId, serviceHub)
    serviceHub.withEntityManager {
        if (request.requestStatus != null || request.requestStatus == RequestStatus.REQUESTED) {
            flowFail("Cannot set the transaction id for requests with no request status or REQUESTED request status")
        } else if (request.txId != null) {
            flowFail("Transaction id has already been set for request id $requestId")
        }
        request.txId = txId
        request.unsignedTransaction = tx
        merge(request)
    }
}

/**
 * Create a new [ExchangeRequest] from [ExchangeRequestDTO] object in the exchange_request table in the db of
 * the local node
 * @param exchangeRequestDTO
 */
@Suspendable
fun FlowLogic<*>.newExchangeRequestFromDto(exchangeRequestDTO: ExchangeRequestDTO) {
    ExchangeRequestService.logger.info("exchange request dto : $exchangeRequestDTO")
    val exchangeRequestEntity = exchangeRequestDTO.toExchangeRequestEntity()
    if (exchangeRequestDTO.requestStatus == null) {
        exchangeRequestEntity.requestStatus = RequestStatus.REQUESTED
    }
    serviceHub.withEntityManager {
        persist(exchangeRequestEntity)
    }
}

/**
 * Checks if the specified asset is owned by our identity
 * @param abstractAssetRequest to be checked against our vault
 * @param holder the identity to be checked against
 */
@Suspendable
fun FlowLogic<*>.isExchangeAssetOwned(abstractAssetRequest: AbstractAssetRequest, holder: AbstractParty): Boolean {

    return with(abstractAssetRequest) {
        when (this) {
            is FungibleAssetRequest -> {
                val heldTokenCriteria = heldTokenAmountCriteria(tokenAmount.token, holder).and(sumTokenCriteria())
                val results = serviceHub.vaultService.queryBy(FungibleToken::class.java, heldTokenCriteria)
                rowsToAmount(tokenAmount.token, uncheckedCast(results)) >= tokenAmount
            }
            is NonFungibleAssetRequest -> {
                val nonFungibleToken: Vault.Page<NonFungibleToken> =
                    uncheckedCast(getMatchingAssetsAgainstRequest(abstractAssetRequest, holder))
                nonFungibleToken.states.single().state.data.holder == holder
            }
            else -> argFail("Cannot determine asset request type. It needs to be either a regular fungible or non fungible asset")
        }
    }
}

/**
 * Get matching Assets from an [AbstractAssetRequest]
 * @param abstractAssetRequest to use to search
 * @param holder of the asset
 */
@Suspendable
fun FlowLogic<*>.getMatchingAssetsAgainstRequest(
    abstractAssetRequest: AbstractAssetRequest,
    holder: AbstractParty
): Vault.Page<AbstractToken> {
    return with(abstractAssetRequest) {
        when (this) {
            is FungibleAssetRequest -> {
                val heldTokenCriteria = heldTokenAmountCriteria(tokenAmount.token, holder)
                serviceHub.vaultService.queryBy(FungibleToken::class.java, heldTokenCriteria)
            }
            is NonFungibleAssetRequest -> {
                val query = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    status = Vault.StateStatus.UNCONSUMED,
                    relevancyStatus = Vault.RelevancyStatus.RELEVANT
                )
                serviceHub.vaultService.queryBy(NonFungibleToken::class.java, query)
            }
            else -> argFail("Cannot determine asset request type. It needs to be either a regular fungible or non fungible asset")
        }
    }
}

/**
 * Find out the relevant [TokenType] from the [AbstractAssetRequest]
 * @param abstractAssetRequest to use
 */
@Suspendable
fun FlowLogic<*>.getTokenTypeFromAssetRequest(abstractAssetRequest: AbstractAssetRequest): TokenType {
    return with(abstractAssetRequest) {
        when (this) {
            is FungibleAssetRequest -> tokenAmount.token
            is NonFungibleAssetRequest -> {
                val query = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    status = Vault.StateStatus.UNCONSUMED,
                    relevancyStatus = Vault.RelevancyStatus.ALL
                )
                val queryBy = serviceHub.vaultService.queryBy(NonFungibleToken::class.java, query)
                queryBy.states.single().state.data.token
            }
            else -> argFail("Cannot determine asset request type. It needs to be either a regular fungible or non fungible asset")
        }
    }
}

/**
 * Find the [ExchangeRequest] entity with the transaction id and convert it into a [ExchangeRequestDTO]
 * @param transactionId to fetch the [ExchangeRequest] by
 */
@Suspendable
fun FlowLogic<*>.getExchangeRequestByTxId(transactionId: String): ExchangeRequestDTO {
    val queryResults = serviceHub.withEntityManager {
        val query = criteriaBuilder.createQuery(ExchangeRequest::class.java).apply {
            val root = from(ExchangeRequest::class.java)
            val txIdEqPredicate = criteriaBuilder.equal(root.get<String>("txId"), transactionId)
            where(criteriaBuilder.and(txIdEqPredicate))
            select(root)
        }
        createQuery(query).resultList
    }
    return ExchangeRequestDTO.fromExchangeRequestEntity(queryResults.first())
}

/**
 * Extract the notary signature from the buyer transaction as configured in the [LockState]
 * of the encumbered transaction
 * @param sellerEncumberedTx containing information about buyer's notary in the [LockState]
 * @param buyerTx finalised
 */
fun FlowLogic<*>.getNotarySigFromEncumberedTx(
    sellerEncumberedTx: SignedTransaction,
    buyerTx: SignedTransaction
): TransactionSignature {
    val toLedgerTransaction = sellerEncumberedTx.toLedgerTransaction(serviceHub)
    val lockState = toLedgerTransaction.outputsOfType(LockState::class.java).single()
    return buyerTx
        .sigs
        .single { it.by == lockState.controllingNotary.owningKey }

}

/**
 * Utility to get transaction deadline as configured in [LockState]
 * @param signedEncumberedTx containing a single [LockState] with a [TimeWindow]
 */
fun getDeadlineFromLockState(signedEncumberedTx: SignedTransaction) : Instant =
    signedEncumberedTx.coreTransaction.outputsOfType<LockState>().single().timeWindow.untilTime!!
