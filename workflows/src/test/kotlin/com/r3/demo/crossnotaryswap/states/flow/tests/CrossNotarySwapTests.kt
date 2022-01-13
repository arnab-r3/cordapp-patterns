package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.InitiateExchangeFlows
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.flows.dto.FungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NonFungibleAssetRequest
import com.r3.demo.crossnotaryswap.flows.utils.INR
import com.r3.demo.crossnotaryswap.flows.utils.getRequestEntityById
import com.r3.demo.crossnotaryswap.states.KittyToken
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

        partyANode.issueFungibleTokens(100, "INR", partyBNode.legalIdentity(), emptyList())

        val sampleTokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(partyCNode.legalIdentity().toString())
        )

        val defineNonFungibleToken = partyCNode.defineNonFungibleToken(sampleTokenDefinition).getOrThrow()
        val tokenDefinitionId = defineNonFungibleToken.singleOutput<KittyToken>().state.data.linearId.toString()
        val issueTxn =
            partyCNode.issueNonFungibleToken(tokenDefinitionId, KittyToken::class.java, partyDNode.legalIdentity())
                .getOrThrow()

        val nftTokenId = issueTxn.singleOutput<NonFungibleToken>().state.data.linearId.toString()

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
            ExchangeRequestDTO.fromExchangeRequestEntity(getRequestEntityById(exchangeRequestId, partyDNode.services))
        val exchangeRequestAtSeller =
            ExchangeRequestDTO.fromExchangeRequestEntity(getRequestEntityById(exchangeRequestId, partyBNode.services))

        assertEquals(exchangeRequestAtBuyer, exchangeRequestAtSeller)
    }


}