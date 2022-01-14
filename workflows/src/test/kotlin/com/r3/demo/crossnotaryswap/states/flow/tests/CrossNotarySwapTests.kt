package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.DraftTransferOfOwnership
import com.r3.demo.crossnotaryswap.flows.InitiateExchangeFlows
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.INR
import com.r3.demo.crossnotaryswap.states.KittyToken
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

    private lateinit var requestId: String
    private lateinit var kittyTokenType: TokenType

    @Before
    override fun initialiseNodes() {
        centralBankNode = nodesByName["CentralBank"]!!
        sellerNode = nodesByName["Seller"]!!
        artistNode = nodesByName["Artist"]!!
        buyerNode = nodesByName["Buyer"]!!
        notaryANode = nodesByName["NotaryA"]!!
        notaryBNode = nodesByName["NotaryB"]!!
    }


    @Test
    fun `initiate cross notary swap`() {


        centralBankNode.issueFungibleTokens(100, "INR", sellerNode.legalIdentity(), emptyList())

        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(artistNode.legalIdentity().toString())
        )
        val defineNonFungibleToken = artistNode.defineNonFungibleToken(tokenDefinition).getOrThrow()
        val tokenDefinitionId = defineNonFungibleToken.singleOutput<KittyToken>().getLinearId()
        val issueTxn = artistNode
            .issueNonFungibleToken(
                tokenDefinitionId,
                KittyToken::class.java,
                buyerNode.legalIdentity()
            )
            .getOrThrow()

        val nftTokenId = issueTxn.singleOutput<NonFungibleToken>().getLinearId()
        kittyTokenType = issueTxn.singleOutput<NonFungibleToken>().state.data.tokenType
        // party D is the buyer and exchanging the NFT with the above id in exchange of 10 INR token
        val exchangeRequestId = buyerNode.startFlow(
            InitiateExchangeFlows.ExchangeRequesterFlow(
                sellerParty = sellerNode.legalIdentity(),
                sellerAssetRequest = FungibleAssetRequest(10.INR),
                buyerAssetRequest = NonFungibleAssetRequest(nftTokenId)
            )
        ).getOrThrow()

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
        val unsignedWireTx = buyerNode.startFlow(DraftTransferOfOwnership(requestId)).getOrThrow()
        val nonFungibleTokens = unsignedWireTx.outputsOfType(NonFungibleToken::class.java)
        assertNotEquals(emptyList(), nonFungibleTokens)
        assertEquals(artistNode.legalIdentity(), nonFungibleTokens.single().token.issuer)
        assertEquals(kittyTokenType issuedBy artistNode.legalIdentity(), nonFungibleTokens.single().token)
        assertEquals(sellerNode.legalIdentity(), nonFungibleTokens.single().holder)
    }

}