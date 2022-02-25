package com.r3.demo.contractdependency.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.contractdependency.contracts.BloomFilterContract
import com.r3.demo.contractdependency.states.BloomFilterState
import com.r3.demo.generic.getDefaultNotary
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@InitiatingFlow
@StartableByRPC
class CreateBloomFilterFlow : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val bloomFilterState = BloomFilterState(listOf(ourIdentity))
        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
            .addOutputState(bloomFilterState)
            .addCommand(BloomFilterContract.BloomFilterCommands.CreateFilter(), ourIdentity.owningKey)

        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(transaction = signedTx, sessions = emptyList()))
    }
}

@InitiatingFlow
@StartableByRPC
class UpdateBloomFilterFlow(private val linearId: String, private val data: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val queriedStates =
            serviceHub.vaultService.queryBy<BloomFilterState>(QueryCriteria
                .LinearStateQueryCriteria(uuid = listOf(UUID.fromString(linearId))))

        require(queriedStates.states.size == 1) {"Expecting a single Bloom filter state with id: $linearId"}

        val inputState = queriedStates.states.single()
        val outputState = inputState.state.data.copy(data, listOf(ourIdentity))

        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
            .addInputState(inputState)
            .addOutputState(outputState)
            .addCommand(BloomFilterContract.BloomFilterCommands.UpdateFilter(), ourIdentity.owningKey)

        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(transaction = signedTx, sessions = emptyList()))
    }
}

@InitiatingFlow
@StartableByRPC
class CheckElementInFilterFlow(private val linearId: String, private val data: String) : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {

        val queriedStates =
            serviceHub.vaultService.queryBy<BloomFilterState>(QueryCriteria
                .LinearStateQueryCriteria(uuid = listOf(UUID.fromString(linearId))))

        require(queriedStates.states.size == 1) {"Expecting a single Bloom filter state with id: $linearId"}

        val inputState = queriedStates.states.single()
        return inputState.state.data.checkContains(data)

    }
}
