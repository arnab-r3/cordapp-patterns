package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.demo.crossnotaryswap.flow.helpers.MockNetworkTest
import com.r3.demo.crossnotaryswap.flow.helpers.legalIdentity
import com.r3.demo.crossnotaryswap.flow.helpers.singleOutput
import com.r3.demo.crossnotaryswap.flows.CurrencyFlows
import com.r3.demo.crossnotaryswap.flows.InitiateExchangeFlows
import com.r3.demo.crossnotaryswap.flows.NFTFlows
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.INR
import com.r3.demo.crossnotaryswap.services.ExchangeRequestService
import com.r3.demo.crossnotaryswap.states.KittyToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CrossNotarySwapTests : MockNetworkTest(numberOfNodes = 4, numberofNotaryNodes = 2) {

    companion object {
        val logger = contextLogger()
    }

    private lateinit var partyANode: StartedMockNode
    private lateinit var partyBNode: StartedMockNode
    private lateinit var partyCNode: StartedMockNode
    private lateinit var partyDNode: StartedMockNode

    private lateinit var notaryANode: StartedMockNode
    private lateinit var notaryBNode: StartedMockNode

    @Before
    override fun initialiseNodes() {
        partyANode = nodesByName["PartyA"]!!
        partyBNode = nodesByName["PartyB"]!!
        partyCNode = nodesByName["PartyC"]!!
        partyDNode = nodesByName["PartyD"]!!
        notaryANode = nodesByName["NotaryA"]!!
        notaryBNode = nodesByName["NotaryB"]!!
    }

    @Test
    fun `initiate cross notary swap`() {

        issueFiat(partyANode, partyBNode)
        val nftTxn = issueNFT(partyCNode, partyDNode)
        val nftTokenId = nftTxn.singleOutput<NonFungibleToken>().state.data.linearId.toString()

        // party D is the buyer and exchanging the NFT with the above id in exchange of 10 INR token
        val exchangeRequestId = partyDNode.startFlow(
            InitiateExchangeFlows.ExchangeRequesterFlow(
                sellerParty = partyBNode.legalIdentity(),
                sellerAssetRequest = FungibleAssetRequest(10.INR),
                buyerAssetRequest = NonFungibleAssetRequest(nftTokenId)
            )
        ).getOrThrow()

        network.waitQuiescent()

        val exchangeRequestAtBuyer =
            partyDNode.services.cordaService(ExchangeRequestService::class.java).getRequestById(exchangeRequestId)

        val exchangeRequestAtSeller =
            partyBNode.services.cordaService(ExchangeRequestService::class.java).getRequestById(exchangeRequestId)

        assertEquals(exchangeRequestAtBuyer, exchangeRequestAtSeller)
    }

    private fun issueFiat(issuerNode: StartedMockNode, holderNode: StartedMockNode): SignedTransaction {
        val transaction = issuerNode.startFlow(
            CurrencyFlows.IssueFiatCurrencyFlow(
                amount = 100,
                currency = "INR",
                receiver = holderNode.legalIdentity())
        ).getOrThrow()
        network.waitQuiescent()
        return transaction
    }

    private fun issueNFT(issuerNode: StartedMockNode, holderNode: StartedMockNode): SignedTransaction {
        // define token
        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(issuerNode.legalIdentity().toString())
        )

        val defineTxn = issuerNode.startFlow(
            NFTFlows.DefineNFTFlow(tokenDefinition)
        ).getOrThrow()

        network.waitQuiescent()

        // issue token to party B
        val tokenDefinitionId = defineTxn.singleOutput<KittyToken>().state.data.linearId.toString()

        logger.info("Created a token type of Kitty with ID: $tokenDefinitionId")

        val issueTxn = issuerNode.startFlow(
            NFTFlows.IssueNFTFlow(
                tokenIdentifier = tokenDefinitionId,
                tokenClass = KittyToken::class.java,
                receivingParty = holderNode.legalIdentity()
            )
        ).getOrThrow()
        val nftId = issueTxn.singleOutput<NonFungibleToken>().state.data.linearId
        logger.info("Issued a token to ${holderNode.legalIdentity()} with NFT ID: $nftId")

        network.waitQuiescent()
        return issueTxn
    }

}