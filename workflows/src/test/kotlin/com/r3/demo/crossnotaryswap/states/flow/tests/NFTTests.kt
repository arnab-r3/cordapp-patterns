package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.NFTFlows
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.states.KittyToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class NFTTests : MockNetworkTest(numberOfNodes = 4, numberofNotaryNodes = 2) {

    private lateinit var partyANode: StartedMockNode
    private lateinit var partyBNode: StartedMockNode
    private lateinit var partyCNode: StartedMockNode
    private lateinit var partyDNode: StartedMockNode

    private lateinit var notaryANode : StartedMockNode
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
    fun `create nft without observers`() {

        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(partyANode.legalIdentity().toString())
        )

        val transaction = partyANode.startFlow(
            NFTFlows.DefineNFTFlow(tokenDefinition)
        ).getOrThrow()

        network.waitQuiescent()

        val dummyKittyToken = tokenDefinition.toKittyToken(partyANode.services)

        assertEquals(
            dummyKittyToken.kittyName,
            transaction.singleOutput<KittyToken>().state.data.kittyName
        )
        assertEquals(
            dummyKittyToken.participants,
            transaction.singleOutput<KittyToken>().state.data.participants
        )

        assertTransactionUsesNotary(transaction, network, notaryBNode)

        assertHasTransaction(transaction, network, partyANode)
        assertNotHasTransaction(transaction, network, partyBNode, partyCNode, partyDNode)

    }

    @Test
    fun `create nft with observers`(){
        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(partyANode.legalIdentity().toString())
        )

        val transaction = partyANode.startFlow(
            NFTFlows.DefineNFTFlow(tokenDefinition, listOf(
                partyDNode.legalIdentity(),
                partyBNode.legalIdentity()
            ))
        ).getOrThrow()

        network.waitQuiescent()

        val dummyKittyToken = tokenDefinition.toKittyToken(partyANode.services)

        assertEquals(
            dummyKittyToken.kittyName,
            transaction.singleOutput<KittyToken>().state.data.kittyName
        )
        assertEquals(
            dummyKittyToken.participants,
            transaction.singleOutput<KittyToken>().state.data.participants
        )

        assertTransactionUsesNotary(transaction, network, notaryBNode)

        assertHasTransaction(transaction, network, partyANode, partyBNode, partyDNode)
        assertNotHasTransaction(transaction, network, partyCNode)
    }
}