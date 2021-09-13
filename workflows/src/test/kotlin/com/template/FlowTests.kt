package com.template

import com.r3.demo.stateencapsulation.contracts.EncapsulatedState
import com.r3.demo.stateencapsulation.contracts.EncapsulatingState
import com.r3.demo.stateencapsulation.flows.EncapsulationDemoFlows
import com.r3.demo.stateencapsulation.flows.EncapsulationDemoFlows.InitiatorFlow.ExampleTransactionObject
import com.r3.examples.testing.Identities
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.test.assertEquals
import kotlin.test.assertFalse


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.demo.stateencapsulation.flows"),
                TestCordapp.findCordapp("com.r3.demo.stateencapsulation.contracts")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
        aliceNode = network.createPartyNode(Identities.ALICE.name)
        bobNode = network.createPartyNode(Identities.BOB.name)
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }
    @Test
    fun `Test E2E Flows`() {


        val flow = EncapsulationDemoFlows.InitiatorFlow("CreateEncapsulated", bobNode.info.singleIdentity(), ExampleTransactionObject(
            enclosedValue = "inner value"
        ))
        val future = aliceNode.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        //successful query means the state is stored at node b's vault. Flow went through.
        val inputCriteria: QueryCriteria = QueryCriteria.LinearStateQueryCriteria()

        val encapsulatedStatesBob = bobNode.services.vaultService.queryBy<EncapsulatedState>(inputCriteria).states
        assertFalse (encapsulatedStatesBob.isEmpty(), "Encapsulated states in Bob's node should not be empty")

        val encapsulatedStatesAlice = aliceNode.services.vaultService.queryBy<EncapsulatedState>(inputCriteria).states
        assertFalse (encapsulatedStatesAlice.isEmpty(), "Encapsulated states in Alice's node should not be empty")


        // Create encapsulating flow

        val innerUUID = encapsulatedStatesBob.single().state.data.linearId.id
        val outerFlow = EncapsulationDemoFlows.InitiatorFlow("CreateEncapsulating", bobNode.info.singleIdentity(), ExampleTransactionObject(
            innerIdentifier = innerUUID,
            enclosingValue = "outer value"

        ))
        val outerFuture = aliceNode.startFlow(outerFlow)
        network.runNetwork()
        outerFuture.getOrThrow()

        val encapsulatingStatesBob = bobNode.services.vaultService.queryBy<EncapsulatingState>(inputCriteria).states
        assertFalse (encapsulatingStatesBob.isEmpty(), "Encapsulating states in Bob's node should not be empty")

        val encapsulatingStatesAlice = aliceNode.services.vaultService.queryBy<EncapsulatingState>(inputCriteria).states
        assertFalse (encapsulatingStatesAlice.isEmpty(), "Encapsulating states in Alice's node should not be empty")


        // Update encapsulated flow


        val updateInnerFlow = EncapsulationDemoFlows.InitiatorFlow("UpdateEncapsulated", bobNode.info.singleIdentity(), ExampleTransactionObject(
           enclosedValue = "new inner value",
           innerIdentifier = innerUUID
        ))
        val updateInnerFuture = aliceNode.startFlow(updateInnerFlow)
        network.runNetwork()
        updateInnerFuture.getOrThrow()

        val encapsulatedStatesBobUpdated = bobNode.services.vaultService.queryBy<EncapsulatedState>(inputCriteria).states
        assertEquals ("new inner value", encapsulatedStatesBobUpdated.single().state.data.innerValue, "Encapsulated states should be updated with message")
        assertEquals (innerUUID, encapsulatedStatesBobUpdated.single().state.data.linearId.id, "Encapsulated states should be updated with same id")

        val encapsulatedStatesAliceUpdated = aliceNode.services.vaultService.queryBy<EncapsulatedState>(inputCriteria).states
        assertEquals ("new inner value", encapsulatedStatesAliceUpdated.single().state.data.innerValue, "Encapsulated states should be updated with message")
        assertEquals (innerUUID, encapsulatedStatesAliceUpdated.single().state.data.linearId.id, "Encapsulated states should be updated with same id")


        // Update Encapsulating

        val outerUUID = encapsulatingStatesBob.single().state.data.linearId.id

        val updateOuterFlow = EncapsulationDemoFlows.InitiatorFlow("UpdateEncapsulating", bobNode.info.singleIdentity(), ExampleTransactionObject(
            enclosingValue = "new outer value",
            innerIdentifier = innerUUID,
            outerIdentifier = outerUUID
        ))

        val updateOuterFuture = aliceNode.startFlow(updateOuterFlow)
        network.runNetwork()
        updateOuterFuture.getOrThrow()

        val encapsulatingStatesBobUpdated = bobNode.services.vaultService.queryBy<EncapsulatingState>(inputCriteria).states
        assertEquals ("new outer value", encapsulatingStatesBobUpdated.single().state.data.outerValue, "Encapsulating states should be updated with message")
        assertEquals (outerUUID, encapsulatingStatesBobUpdated.single().state.data.linearId.id, "Encapsulating states should be updated with same id")

        val encapsulatingStatesAliceUpdated = aliceNode.services.vaultService.queryBy<EncapsulatingState>(inputCriteria).states
        assertEquals ("new outer value", encapsulatingStatesAliceUpdated.single().state.data.outerValue, "Encapsulating states should be updated with message")
        assertEquals (outerUUID, encapsulatingStatesAliceUpdated.single().state.data.linearId.id, "Encapsulating states should be updated with same id")

    }

}