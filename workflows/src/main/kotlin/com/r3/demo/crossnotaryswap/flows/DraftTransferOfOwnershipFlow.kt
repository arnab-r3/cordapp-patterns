package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.utils.generateWireTransactionMerkleTree
import com.r3.demo.crossnotaryswap.flows.utils.getDependencies
import com.r3.demo.crossnotaryswap.services.ExchangeRequestService
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getDefaultTimeWindow
import com.r3.demo.generic.getPreferredNotaryForToken
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap

/**
 * The **buyer** would present the draft transfer of ownership to the seller
 * @param requestId of the negotiated [ExchangeRequestDTO]
 */
@InitiatingFlow
@StartableByRPC
@StartableByService
class DraftTransferOfOwnershipFlow(
    private val requestId: String
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // fetch request details
        val exchangeService = serviceHub.cordaService(ExchangeRequestService::class.java)
        val exchangeRequestDto = exchangeService.getRequestById(requestId)

        // construct the unsigned wire transaction
        val transactionBuilder = TransactionBuilder(getPreferredNotaryForToken(exchangeRequestDto.buyerAsset.tokenType))
        val constructedTxForTransfer =
            addBuyerAssetToTransactionBuilder(transactionBuilder, exchangeRequestDto)

        // add time window
        constructedTxForTransfer.setTimeWindow(getDefaultTimeWindow(serviceHub))
        //convert to wire tx
        val unsignedWireTx = constructedTxForTransfer.toWireTransaction(serviceHub)

        // initiate the session with the seller and send the wire transaction
        val sellerSession = initiateFlow(exchangeRequestDto.seller)
        sellerSession.send(unsignedWireTx)

        // send the dependencies of the wire transaction too
        val txDependencies = unsignedWireTx.getDependencies()
        txDependencies.forEach {
            val validatedTxDependency = serviceHub.validatedTransactions.getTransaction(it)
                ?: flowFail("Unable to find validated transaction $it")
            subFlow(SendTransactionFlow(sellerSession, validatedTxDependency))
        }

        val txOk = sellerSession.receive<Boolean>().unwrap { it }
        if (!txOk) flowFail("Failed to exchange unsigned transaction with the seller")
    }

    @Suspendable
    private fun addBuyerAssetToTransactionBuilder(
        transactionBuilder: TransactionBuilder,
        exchangeRequestDto: ExchangeRequestDTO
    ): TransactionBuilder {
        val buyerAsset = exchangeRequestDto.buyerAsset
        val seller = exchangeRequestDto.seller
        return when {
            // add the move tokens if it is a nft
            buyerAsset.tokenType.isRegularTokenType() -> {
                val tokenType = exchangeRequestDto.buyerAsset.tokenType
                val amount = exchangeRequestDto.buyerAsset.amount!!.quantity
                val partyAndAmount = PartyAndAmount(seller, amount of tokenType)
                addMoveFungibleTokens(
                    transactionBuilder = transactionBuilder,
                    serviceHub = serviceHub,
                    partiesAndAmounts = listOf(partyAndAmount),
                    changeHolder = exchangeRequestDto.buyer,
                    queryCriteria = null)
            }
            // add the move tokens if it is a fungible token
            buyerAsset.tokenType.isPointer() -> {
                val partyAndToken = PartyAndToken(seller, exchangeRequestDto.buyerAsset.tokenType)
                addMoveNonFungibleTokens(
                    transactionBuilder = transactionBuilder,
                    serviceHub = serviceHub,
                    partyAndToken = partyAndToken)
            }
            else -> flowFail("Cannot support move operation for a token which is neither a Regular token or a NFT")
        }
    }
}


@InitiatedBy(DraftTransferOfOwnershipFlow::class)
class DraftTransferOfOwnershipHandler(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val unsignedWireTx = counterPartySession.receive<WireTransaction>().unwrap { it }

        val txOk =
            receiveAndVerifyTxDependencies(counterPartySession, unsignedWireTx) && verifyShareConditions(unsignedWireTx)
                    && verifySharedTx(unsignedWireTx)
        if (!txOk) flowFail("Failed to validate the proposed transaction or one of its dependencies")

        // respond to the buyer that the transaction is verified and ok.
        counterPartySession.send(txOk)

        // TODO start the offer encumbered tokens flow

        val notaryIdentity = unsignedWireTx.notary!!
        val notarySignatureMetadata = getSignatureMetadata(notaryIdentity)

        val validatedDraftTransferOfOwnership = ValidatedDraftTransferOfOwnership(tx = unsignedWireTx,
            controllingNotary = notaryIdentity,
            notarySignatureMetadata = notarySignatureMetadata)


    }


    /**
     * Retrieve the identity and signature metadata to be associated with a node from the network map
     * @param party to get the signature metadata for
     */
    @Suspendable
    private fun getSignatureMetadata(party: Party): SignatureMetadata {
        val nodeInfo = serviceHub.networkMapCache.getNodeByLegalIdentity(party)
            ?: flowFail("Unable to fetch notary node details from network map $party")
        return SignatureMetadata(nodeInfo.platformVersion,
            Crypto.findSignatureScheme(party.owningKey).schemeNumberID)


    }

    /**
     * Receive and verify all [wireTransaction]'s dependencies.
     * @param otherSession the session with the other party.
     * @param wireTransaction the transaction whose dependencies are to be verified.
     * @return true if all dependencies can be successfully validated, false otherwise.
     */
    @Suspendable
    private fun receiveAndVerifyTxDependencies(otherSession: FlowSession, wireTransaction: WireTransaction): Boolean {
        return wireTransaction.getDependencies().all {
            try {
                subFlow(ReceiveTransactionFlow(otherSession))
                true
            } catch (e: Exception) {
                logger.warn("Failed to resolve input transaction ${it.toHexString()}: ${e.message}")
                false
            }
        }
    }

    /**
     * Verify whether the transaction meets the minimum requirements for sharing with the other network's trusting
     * Party.
     * @param wireTransaction the [WireTransaction] to verify.
     * @return true if the requirements are met, false otherwise.
     */
    @Suspendable
    private fun verifyShareConditions(wireTransaction: WireTransaction): Boolean {
        val id = wireTransaction.id
        val suppliedMerkleTree = wireTransaction.merkleTree
        val timeWindow = wireTransaction.timeWindow
        val notary = wireTransaction.notary
        val expectedMerkleTree = wireTransaction.generateWireTransactionMerkleTree()

        return !listOf(
            (expectedMerkleTree != suppliedMerkleTree) to
                    "The supplied merkle tree ($suppliedMerkleTree) did not match the expected merkle tree ($expectedMerkleTree)",
            (id != suppliedMerkleTree.hash) to
                    "The supplied merkle tree hash (${suppliedMerkleTree.hash}) did not match the supplied id (${id})",
            (timeWindow == null) to
                    "Time window must be provided",
            (notary == null) to
                    "Notary must be provided"
        ).any {
            if (it.first) {
                logger.warn("Failed to process shared transaction $id: ${it.second}")
            }
            it.first
        }
    }

    /**
     * Verify the [WireTransaction] can be fully resolved, verified and its contract code successfully executed.
     * @param wireTransaction the [WireTransaction] to verify.
     * @return true if successfully verified, false otherwise.
     */
    @Suspendable
    private fun verifySharedTx(wireTransaction: WireTransaction): Boolean {
        val ledgerTx = wireTransaction.toLedgerTransaction(serviceHub)
        return try {
            ledgerTx.verify()
            true
        } catch (e: Exception) {
            logger.warn("Failed to resolve transaction: ${e.message}")
            false
        }
    }
}