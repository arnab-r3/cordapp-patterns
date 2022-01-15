package com.r3.demo.crossnotaryswap.states.flow.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.DraftTransferOfOwnership
import com.r3.demo.crossnotaryswap.flows.InitiateExchangeFlows
import com.r3.demo.crossnotaryswap.flows.OfferEncumberedTokens
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.INR
import com.r3.demo.crossnotaryswap.states.KittyToken
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.crossnotaryswap.states.ValidatedDraftTransferOfOwnership
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SignableData
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertEquals

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

    private lateinit var centralBankNode: StartedMockNode
    private lateinit var sellerNode: StartedMockNode
    private lateinit var artistNode: StartedMockNode
    private lateinit var buyerNode: StartedMockNode

    private lateinit var notaryANode: StartedMockNode
    private lateinit var notaryBNode: StartedMockNode


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

    @Test
    fun `initiate cross notary swap`() {


        centralBankNode.issueFungibleTokens(100, "INR", sellerNode.legalIdentity(), emptyList())

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
                KittyToken::class.java,
                buyerNode.legalIdentity()
            )
            .getOrThrow()

        val nftTokenId = issueTxn.singleOutput<NonFungibleToken>().getLinearId()

        logger.info("Artist issued a kitty token with NFT linear id: $nftTokenId")

        kittyTokenType = issueTxn.singleOutput<NonFungibleToken>().state.data.tokenType
        // party D is the buyer and exchanging the NFT with the above id in exchange of 10 INR token
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
    private fun testLockState(compositeKey: PublicKey, lockState: LockState) {
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
        fungibleTokens: List<FungibleToken>,
        compositeKey: PublicKey
    ) {
        assertThat(fungibleTokens.filter { it.holder == sellerNode.legalIdentity() }.size, lessThanOrEqualTo(1))
        assertThat(fungibleTokens.filter { it.holder.owningKey == compositeKey }.size, greaterThanOrEqualTo(1))
        val totalAmountTransferred =
            fungibleTokens
                .filter { it.holder.owningKey == compositeKey }
                .fold(BigDecimal.ZERO) { acc, fungibleToken ->
                    acc + fungibleToken.amount.toDecimal()
                }
        val exchangeRequestDTO = getExchangeRequestDto(requestId, sellerNode)
        val requestedAmount = (exchangeRequestDTO.sellerAssetRequest as FungibleAssetRequest).tokenAmount.toDecimal()
        assertThat(totalAmountTransferred, equalTo(requestedAmount))
    }


    private fun testCyclicalEncumbrance(coreTransaction: CoreTransaction, compositeKey: PublicKey) {
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

        encumberedTx.coreTransaction.run {
            assertThat(outputsOfType<LockState>(), hasSize(equalTo(1)))
            val lockState = outputsOfType<LockState>().single()
            assertThat(outputsOfType<FungibleToken>(), hasSize(greaterThanOrEqualTo(1)))
            val fungibleTokens = outputsOfType<FungibleToken>()
            assertThat(inputs, hasSize(greaterThanOrEqualTo(1)))
            val compositeKey = CompositeKey.Builder()
                .addKey(sellerNode.legalIdentity().owningKey, 1)
                .addKey(buyerNode.legalIdentity().owningKey, 1)
                .build(1)
            testLockState(compositeKey, lockState)
            testCyclicalEncumbrance(this, compositeKey)
            testFungibleTokens(fungibleTokens, compositeKey)
        }
    }


    @Test
    fun `sign and finalize buyer transaction`(){
        `test offer encumbered tokens`()

    }
}