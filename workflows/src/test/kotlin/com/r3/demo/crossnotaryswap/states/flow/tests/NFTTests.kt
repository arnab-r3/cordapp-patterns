package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
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

    @Test
    fun `issue and move nft without observers`() {

        // define token
        val tokenDefinition = KittyTokenDefinition(
            kittyName = "Black Kitty",
            maintainers = listOf(partyANode.legalIdentity().toString())
        )

        val defineTxn = partyANode.startFlow(
            NFTFlows.DefineNFTFlow(tokenDefinition)
        ).getOrThrow()

        network.waitQuiescent()

        // issue token to party B
        val tokenDefinitionId = defineTxn.singleOutput<KittyToken>().state.data.linearId.toString()

        val issueTxn = partyANode.startFlow(
            NFTFlows.IssueNFTFlow(
                tokenIdentifier = tokenDefinitionId,
                tokenClass = KittyToken::class.java,
                receivingParty = partyBNode.legalIdentity()
            )
        ).getOrThrow()

        network.waitQuiescent()

        val issueTxnOutput = issueTxn.singleOutput<NonFungibleToken>().state.data

        assertEquals(partyBNode.legalIdentity(), issueTxnOutput.holder)
        assertEquals(partyANode.legalIdentity(), issueTxnOutput.issuer)

        assertTransactionUsesNotary(issueTxn, network, notaryBNode)
        assertHasTransaction(issueTxn, network, partyANode, partyBNode)
        assertNotHasTransaction(issueTxn, network, partyCNode, partyDNode)


        // move token to party D
        val moveTransaction = partyBNode.startFlow(
            NFTFlows.MoveNFTFlow(
                partyAndToken = PartyAndToken(
                    party = partyDNode.legalIdentity(),
                    token = issueTxnOutput.issuedTokenType.tokenType
                )
            )
        ).getOrThrow()

        network.waitQuiescent()
        val moveTxnOutput = moveTransaction.singleOutput<NonFungibleToken>().state.data


        assertEquals(partyDNode.legalIdentity(), moveTxnOutput.holder)
        assertEquals(partyANode.legalIdentity(), moveTxnOutput.issuer)
        assertHasTransaction(moveTransaction, network, partyDNode, partyBNode)
        assertNotHasTransaction(moveTransaction, network, partyANode, partyCNode)

    }

}