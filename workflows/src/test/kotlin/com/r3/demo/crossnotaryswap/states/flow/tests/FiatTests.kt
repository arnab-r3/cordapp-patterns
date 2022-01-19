package com.r3.demo.crossnotaryswap.states.flow.tests

import com.r3.demo.crossnotaryswap.flow.helpers.*
import com.r3.demo.crossnotaryswap.flows.CurrencyFlows
import com.r3.demo.crossnotaryswap.flows.utils.INR
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class FiatTests : MockNetworkTest(numberOfNodes = 4, numberofNotaryNodes = 2) {

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
    fun `issue fiat tokens to self without observers`() {

        val transaction = partyANode.startFlow(
            CurrencyFlows.IssueFiatCurrencyFlow(
                amount = BigDecimal(100.20),
                currency = "INR",
                receiver = partyANode.legalIdentity())
        ).getOrThrow()

        assertTransactionUsesNotary(transaction, network, notaryANode)
        assertHasTransaction(transaction, network, partyANode)
        assertNotHasTransaction(transaction, network, partyBNode, partyCNode, partyDNode)

    }

    @Test
    fun `issue fiat tokens to self with observers`() {

        val transaction = partyANode.startFlow(
            CurrencyFlows.IssueFiatCurrencyFlow(
                amount = BigDecimal(100.20),
                currency = "INR",
                receiver = partyCNode.legalIdentity(),
                observers = listOf(partyBNode.legalIdentity()))
        ).getOrThrow()

        assertTransactionUsesNotary(transaction, network, notaryANode)

        assertHasTransaction(transaction, network, partyANode, partyCNode, partyBNode)
        assertNotHasTransaction(transaction, network, partyDNode)

    }


    @Test
    fun `check balance after issue`() {

        val transaction = partyANode.startFlow(
            CurrencyFlows.IssueFiatCurrencyFlow(
                amount = BigDecimal(100.20),
                currency = "INR",
                receiver = partyANode.legalIdentity())
        ).getOrThrow()

        assertTransactionUsesNotary(transaction, network, notaryANode)
        assertHasTransaction(transaction, network, partyANode)
        assertNotHasTransaction(transaction, network, partyBNode, partyCNode, partyDNode)

        val amount = partyANode.startFlow(
            CurrencyFlows.GetBalanceFlow("INR")
        ).getOrThrow()

        assertEquals(BigDecimal(100.20).INR, amount)

    }

    @Test
    fun `move fiat`() {

        val issueTxn = partyANode.startFlow(
            CurrencyFlows.IssueFiatCurrencyFlow(
                amount = BigDecimal(100.20),
                currency = "INR",
                receiver = partyBNode.legalIdentity())
        ).getOrThrow()

        assertTransactionUsesNotary(issueTxn, network, notaryANode)
        assertHasTransaction(issueTxn, network, partyANode, partyBNode)
        assertNotHasTransaction(issueTxn, network, partyCNode, partyDNode)


        val moveTxn = partyBNode.startFlow(
            CurrencyFlows.MoveFiatTokensFlow(
                currency = "INR",
                amount = 12,
                receiver = partyCNode.legalIdentity(),
                observers = listOf(partyANode.legalIdentity())
            )
        ).getOrThrow()


        assertTransactionUsesNotary(moveTxn, network, notaryANode)
        assertHasTransaction(moveTxn, network, partyANode, partyBNode, partyBNode)
        assertNotHasTransaction(moveTxn, network, partyDNode)


        assertHasBalance(network, BigDecimal(12).INR, partyCNode)
        assertHasBalance(network, BigDecimal(88.20).INR, partyBNode)

    }


}