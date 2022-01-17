package com.r3.demo.crossnotaryswap.flow.helpers

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

abstract class MockNetworkTest(
    private val nodeNames: List<CordaX500Name>,
    private val notaryNames: List<CordaX500Name>
) {

    @get:Rule
    val timeoutRule = Timeout(5, TimeUnit.MINUTES)

    companion object {
        fun createSequentialNodeNames(numberOfNodes: Int): List<CordaX500Name> {
            val partyOrgs = (1..numberOfNodes).map { "Party${it.toChar() + 64}" }.toTypedArray()
            return partyOrgs.map { CordaX500Name(it, "London", "GB") }
        }

        fun createSequentialNotaryNames(numberOfNodes: Int): List<CordaX500Name> {
            val partyOrgs = (1..numberOfNodes).map { "Notary${it.toChar() + 64}" }.toTypedArray()
            return partyOrgs.map { CordaX500Name(it, "London", "GB") }
        }
    }


    constructor(numberOfNodes: Int, numberofNotaryNodes: Int) : this(
        nodeNames = createSequentialNodeNames(numberOfNodes),
        notaryNames = createSequentialNotaryNames(numberofNotaryNodes)
    )

    protected val network = MockNetwork(parameters = MockNetworkParameters(
        cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("com.r3.demo.crossnotaryswap.contracts"),
            TestCordapp.findCordapp("com.r3.demo.crossnotaryswap.flows").withConfig(
                mapOf(
                    "inr_notary" to "O=NotaryA,L=London,C=GB",
                    "kitty_notary" to "O=NotaryB,L=London,C=GB",
                    "wiretx_timewindow" to "600"     // 10 mins time window in seconds
                )
            ),
            TestCordapp.findCordapp("com.r3.corda.lib.ci")),
        threadPerNode = true,
        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
        notarySpecs = notaryNames.map { MockNetworkNotarySpec(it) }
    ))

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>
    protected lateinit var nodesByName: Map<String, StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = nodeNames.map {
            network.createNode(
                legalName = it,
                configOverrides = MockNodeConfigOverrides(extraDataSourceProperties = mapOf(
                    "maximumPoolSize" to "20",
                    "connectionTimeout" to "300000",
                    "minimumIdle" to "20",
                    "maxLifetime" to "600000"
                ))
            )
        }
        val nodeMap = LinkedHashMap<String, StartedMockNode>()
        nodes.forEach { node ->
            nodeMap[node.info.chooseIdentity().name.organisation] = node
        }
        network.notaryNodes.forEach { node ->
            nodeMap[node.info.chooseIdentity().name.organisation] = node
        }
        nodesByName = nodeMap

    }

    @After
    fun tearDownNetwork() {
        // Required to get around mysterious KryoException
        try {
            network.stopNodes()
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    protected val NOTARIES: List<StartedMockNode> get() = network.notaryNodes

    protected fun moveNodesClocks(numOfDays: Long, numOfMinutes: Long = 0) {
        val instant = Instant.now()
        val now = instant.atOffset(ZoneOffset.systemDefault().rules.getOffset(instant))
        this.javaClass.declaredFields.filter {
            it.type == StartedMockNode::class.java
        }.forEach {
            ((it.get(this) as StartedMockNode).services.clock as TestClock).setTo(now
                .plusDays(numOfDays)
                .plusMinutes(numOfMinutes).toInstant()
            )
        }
        network.notaryNodes.forEach {
            (it.services.clock as TestClock).setTo(now.plusMinutes(numOfMinutes).plusDays(numOfDays).toInstant())
        }
    }
}