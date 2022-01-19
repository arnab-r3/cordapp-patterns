package com.r3.demo.crossnotaryswap.states.flow.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.contracts.LockContract
import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.*
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.INR
import com.r3.demo.crossnotaryswap.states.KittyToken
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.NotaryException
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val nodeNames = listOf(
    "O=Buyer,L=London,C=GB",
    "O=Seller,L=London,C=GB",
    "O=Artist,L=London,C=GB",
    "O=CentralBank,L=London,C=GB")
    .map { CordaX500Name.parse(it) }

val notaryNames = listOf(
    "O=NotaryA,L=London,C=GB",
    "O=NotaryB,L=London,C=GB")
    .map { CordaX500Name.parse(it) }

class CrossNotarySwapTests : MockNetworkTest(nodeNames, notaryNames) {

    companion object {
        val logger = contextLogger()
    }

    internal lateinit var centralBankNode: StartedMockNode
    internal lateinit var sellerNode: StartedMockNode
    internal lateinit var artistNode: StartedMockNode
    internal lateinit var buyerNode: StartedMockNode
    internal lateinit var notaryANode: StartedMockNode
    internal lateinit var notaryBNode: StartedMockNode


    @Before
    override fun initialiseNodes() {
        centralBankNode = nodesByName["CentralBank"]!!
        sellerNode = nodesByName["Seller"]!!
        artistNode = nodesByName["Artist"]!!
        buyerNode = nodesByName["Buyer"]!!
        notaryANode = nodesByName["NotaryA"]!!
        notaryBNode = nodesByName["NotaryB"]!!
    }

    private lateinit var requestId: String
    private lateinit var kittyTokenType: TokenType
    private lateinit var unsignedWireTx: WireTransaction
    private lateinit var validatedDraftTransferOfOwnership: ValidatedDraftTransferOfOwnership
    private lateinit var encumberedTx: SignedTransaction
    private lateinit var lockState: LockState
    private lateinit var finalizedBuyerTx: SignedTransaction

    @Test
    fun `initiate cross notary swap`() {


        centralBankNode.issueFungibleTokens(BigDecimal(100.20), "INR", sellerNode.legalIdentity(), emptyList())

        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(artistNode.legalIdentity().toString())
        )
        val defineNonFungibleToken = artistNode.defineNonFungibleToken(tokenDefinition).getOrThrow()
        val tokenDefinitionId = defineNonFungibleToken.singleOutput<KittyToken>().getLinearId()

        logger.info("Artist defined a kitty token with ID: $tokenDefinitionId")

        val issueTxn = artistNode
            .issueNonFungibleToken(
                tokenDefinitionId,
                buyerNode.legalIdentity()
            )
            .getOrThrow()

        val nftTokenId = issueTxn.singleOutput<NonFungibleToken>().getLinearId()

        logger.info("Artist issued a kitty token with NFT linear id: $nftTokenId")

        kittyTokenType = issueTxn.singleOutput<NonFungibleToken>().state.data.tokenType
        // party D is the buyer and exchanging the NFT with the above id in exchange of 10 INR token

        logger.info("Buyer key is: ${buyerNode.legalIdentity().owningKey.toStringShort()}")
        logger.info("Seller key is: ${sellerNode.legalIdentity().owningKey.toStringShort()}")

        val exchangeRequestId = buyerNode.startFlow(
            InitiateExchangeFlows.ExchangeRequesterFlow(
                sellerParty = sellerNode.legalIdentity(),
                sellerAssetRequest = FungibleAssetRequest(10.INR),
                buyerAssetRequest = NonFungibleAssetRequest(nftTokenId)
            )
        ).getOrThrow()

        logger.info("Asset exchange request created with request Id: $exchangeRequestId")

        network.waitQuiescent()

        val exchangeRequestAtBuyer = getExchangeRequestDto(exchangeRequestId, buyerNode)
        val exchangeRequestAtSeller = getExchangeRequestDto(exchangeRequestId, sellerNode)
        assertEquals(exchangeRequestAtBuyer, exchangeRequestAtSeller)
        assertEquals(RequestStatus.REQUESTED, exchangeRequestAtBuyer.requestStatus)
        assertEquals(RequestStatus.REQUESTED, exchangeRequestAtSeller.requestStatus)

        requestId = exchangeRequestId
    }


    @Test
    fun `test approval of exchange request`() {
        `initiate cross notary swap`()
        sellerNode.startFlow(InitiateExchangeFlows.ExchangeResponderFlow(requestId, true))
        network.waitQuiescent()
        val exchangeRequestAtBuyer = getExchangeRequestDto(requestId, buyerNode)
        val exchangeRequestAtSeller = getExchangeRequestDto(requestId, sellerNode)

        assertEquals(exchangeRequestAtBuyer, exchangeRequestAtSeller)
        assertEquals(RequestStatus.APPROVED, exchangeRequestAtSeller.requestStatus)
        assertEquals(RequestStatus.APPROVED, exchangeRequestAtBuyer.requestStatus)
    }

    @Test
    fun `test draft transfer of ownership request`() {
        `test approval of exchange request`()
        val draftTransferOfOwnershipPair =
            buyerNode.startFlow(DraftTransferOfOwnership(requestId)).getOrThrow()

        network.waitQuiescent()
        val now = Instant.now()

        unsignedWireTx = draftTransferOfOwnershipPair.first
        validatedDraftTransferOfOwnership = draftTransferOfOwnershipPair.second

        val generatedNonFungibleTokens = unsignedWireTx.outputsOfType<NonFungibleToken>()
        assertThat(generatedNonFungibleTokens, hasSize(equalTo(1)))
        assertThat(generatedNonFungibleTokens.single().token.issuer, equalTo(artistNode.legalIdentity()))
        assertEquals(sellerNode.legalIdentity(), generatedNonFungibleTokens.single().holder)

        assertThat(
            getExchangeRequestDto(requestId, buyerNode).unsignedWireTransaction,
            equalTo(getExchangeRequestDto(requestId, buyerNode).unsignedWireTransaction))
        assertThat(validatedDraftTransferOfOwnership.controllingNotary, equalTo(notaryBNode.legalIdentity()))
        assertThat(validatedDraftTransferOfOwnership.timeWindow.untilTime!!, greaterThan(now))
    }

    /**
     * helper functions for the next couple of tests
     */
    private fun testLock(
        encumberedTx: SignedTransaction,
        compositeKey: PublicKey
    ) {

        val lockCommand = encumberedTx.tx.commands.find { it.value is LockContract.Encumber }
        assertNotNull(lockCommand)
        assertThat(lockCommand!!.signers.single(), equalTo(compositeKey))

        lockState = encumberedTx.coreTransaction.outputsOfType<LockState>().single()
        assertThat(lockState.compositeKey, equalTo(compositeKey))
        assertThat(lockState.controllingNotary, equalTo(notaryBNode.legalIdentity()))
        assertThat(lockState.timeWindow.untilTime!!,
            greaterThanOrEqualTo(validatedDraftTransferOfOwnership.timeWindow.untilTime!!))
        assertThat(lockState.creator, equalTo(sellerNode.legalIdentity()))
        assertThat(lockState.receiver, equalTo(buyerNode.legalIdentity()))
        assertThat(lockState.txIdWithNotaryMetadata.txId, equalTo(unsignedWireTx.id))
        val signatureMetadata = getSignatureMetadata(notaryBNode.legalIdentity(), sellerNode.services)
        assertThat(lockState.txIdWithNotaryMetadata, equalTo(SignableData(unsignedWireTx.id, signatureMetadata)))
    }

    private fun testFungibleTokens(
        encumberedTx: SignedTransaction,
        owningKey: PublicKey
    ) {
        assertThat(encumberedTx.coreTransaction.outputsOfType<FungibleToken>(), hasSize(greaterThanOrEqualTo(1)))
        val fungibleTokens = encumberedTx.coreTransaction.outputsOfType<FungibleToken>()
        assertThat(fungibleTokens.filter { it.holder == sellerNode.legalIdentity() }.size, lessThanOrEqualTo(1))
        assertThat(fungibleTokens.filter { it.holder.owningKey == owningKey }.size, greaterThanOrEqualTo(1))
        val totalAmountTransferred =
            fungibleTokens
                .filter { it.holder.owningKey == owningKey }
                .fold(BigDecimal.ZERO) { acc, fungibleToken ->
                    acc + fungibleToken.amount.toDecimal()
                }
        val exchangeRequestDTO = getExchangeRequestDto(requestId, sellerNode)
        val requestedAmount = (exchangeRequestDTO.sellerAssetRequest as FungibleAssetRequest).tokenAmount.toDecimal()
        assertThat(totalAmountTransferred, equalTo(requestedAmount))
        assertTransactionUsesNotary(encumberedTx, network, notaryANode)
    }


    private fun testCyclicalEncumbrance(
        encumberedTx: SignedTransaction,
        compositeKey: PublicKey
    ) {
        val coreTransaction = encumberedTx.coreTransaction
        val lockOutputs = coreTransaction.outputs.filter { it.data is LockState }
        val fungibleTokenOutputsToSender = coreTransaction
            .outputs
            .filter { it.data is FungibleToken && (it.data as FungibleToken).holder.owningKey == compositeKey }
        val encumbranceCount = coreTransaction.outputs
            .mapNotNull { it.encumbrance }
            .filter { it < coreTransaction.outputs.size }
            .toSet()
            .size
        assertThat(encumbranceCount, equalTo(lockOutputs.size + fungibleTokenOutputsToSender.size))
    }

    @Test
    fun `test offer encumbered tokens`() {
        `test draft transfer of ownership request`()
        encumberedTx = sellerNode.startFlow(
            OfferEncumberedTokens(requestId, validatedDraftTransferOfOwnership)
        ).getOrThrow()

        assertHasTransaction(encumberedTx, network, sellerNode, buyerNode)
        assertNotHasTransaction(encumberedTx, network, notaryANode, notaryBNode, centralBankNode, artistNode)

        encumberedTx.run {
            assertThat(coreTransaction.inputs, hasSize(greaterThanOrEqualTo(1)))
            val compositeKey = CompositeKey.Builder()
                .addKey(sellerNode.legalIdentity().owningKey, 1)
                .addKey(buyerNode.legalIdentity().owningKey, 1)
                .build(1)

            logger.info("CompositeKey is: ${compositeKey.toStringShort()}")

            testLock(this, compositeKey)
            testCyclicalEncumbrance(this, compositeKey)
            testFungibleTokens(this, compositeKey)
        }
    }


    @Test
    fun `test sign and finalize buyer transaction`() {
        `test offer encumbered tokens`()
        // check if the buyer can unlock the tokens without providing the correct signature of the transfer of his assets
        val invalidSignature = encumberedTx.sigs.find { it.by == notaryANode.legalIdentity().owningKey }!!
        assertThrows<TransactionVerificationException.ContractRejection> {
            buyerNode.startFlow(UnlockEncumberedTokens(requestId, encumberedTx.id, invalidSignature)).getOrThrow()
        }

        finalizedBuyerTx = buyerNode.startFlow(
            SignAndFinalizeTransferOfOwnership(requestId, unsignedWireTx)
        ).getOrThrow()
        assertThat(finalizedBuyerTx.tx, equalTo(unsignedWireTx))
        val notarySignature = finalizedBuyerTx.sigs
            .find { it.by == notaryBNode.legalIdentity().owningKey }
        assertNotNull(notarySignature)
        assertTrue(notarySignature?.verify(unsignedWireTx.id)!!)
        assertThat(notarySignature.signatureMetadata, equalTo(lockState.txIdWithNotaryMetadata.signatureMetadata))
        val transferredNonFungibleTokens = finalizedBuyerTx.coreTransaction.outputsOfType<NonFungibleToken>()
        assertThat(transferredNonFungibleTokens, hasSize(equalTo(1)))
        assertTransactionUsesNotary(finalizedBuyerTx, network, notaryBNode)

    }

    @Test
    fun `test unlock encumbered tokens`() {
        `test sign and finalize buyer transaction`()
        val notarySignatureOnBuyerTx = finalizedBuyerTx.sigs
            .find { it.by == lockState.controllingNotary.owningKey }
        val unlockedTx =
            buyerNode.startFlow(
                UnlockEncumberedTokens(requestId, encumberedTx.id, notarySignatureOnBuyerTx!!)
            ).getOrThrow()
        testFungibleTokens(unlockedTx, buyerNode.legalIdentity().owningKey)
        assertThrows<NotaryException>
            {sellerNode.startFlow(RevertEncumberedTokens(requestId, encumberedTx.id)).getOrThrow()}
    }

    @Test
    fun `test time window expiry for buyer`() {
        `test offer encumbered tokens`()
        moveNodesClocks(0, 20L)
        network.waitQuiescent()
        assertThrows<NotaryException> {
            buyerNode.startFlow(
                SignAndFinalizeTransferOfOwnership(requestId, unsignedWireTx)
            ).getOrThrow()
        }
        val revertedTokensTxn = sellerNode.startFlow(
            RevertEncumberedTokens(requestId, encumberedTx.id)
        ).getOrThrow()
        testFungibleTokens(revertedTokensTxn, sellerNode.legalIdentity().owningKey)
    }
}