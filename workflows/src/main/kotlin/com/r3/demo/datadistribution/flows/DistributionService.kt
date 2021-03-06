package com.r3.demo.datadistribution.flows

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Service to distribute transactions in parallel
 */
@CordaService
class DistributionService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {


    private lateinit var executorService: ExecutorService

    private class CallDistributeTransactionsFlow(
        val serviceHub: AppServiceHub,
        val receiver: Party,
        val signedTransaction: SignedTransaction
    ) : Callable<CordaFuture<Unit>> {
        override fun call(): CordaFuture<Unit> =
            serviceHub.startFlow(DataBroadCastFlows.TransactionBroadcastInitiatorFlow(signedTransaction,
                receiver)).returnValue
    }

    private class CallDistributeStateAndRefsFlow(
        val serviceHub: AppServiceHub,
        val receiver: Party,
        val stateAndRefs: Set<StateAndRef<ContractState>>
    ) : Callable<CordaFuture<Unit>> {
        override fun call(): CordaFuture<Unit> =
            serviceHub.startFlow(DataBroadCastFlows.StateAndRefBroadcastInitiatorFlow(stateAndRefs, receiver)).returnValue
    }

    fun distributeTransactionParallel(signedTransaction: SignedTransaction, parties: Set<Party>) {
        if (!this::executorService.isInitialized) {
            executorService = Executors.newFixedThreadPool(MAX_THREAD_SIZE)
        }
        for (party in parties) {
            executorService.submit(CallDistributeTransactionsFlow(appServiceHub, party, signedTransaction))
        }
    }

    fun distributeTransactionsParallel(signedTransactions: Iterable<SignedTransaction>, parties: Set<Party>) {
        if (!this::executorService.isInitialized) {
            executorService = Executors.newFixedThreadPool(MAX_THREAD_SIZE)
        }
        for (party in parties) {
            for (transaction in signedTransactions) {
                executorService.submit(CallDistributeTransactionsFlow(appServiceHub, party, transaction))
            }
        }
    }

    fun distributeStateAndRefsParallel(stateAndRefs: Set<StateAndRef<ContractState>>, parties: Set<Party>) {
        if (!this::executorService.isInitialized) {
            executorService = Executors.newFixedThreadPool(MAX_THREAD_SIZE)
        }
        for (party in parties) {
            executorService.submit(CallDistributeStateAndRefsFlow(appServiceHub, party, stateAndRefs))

        }
    }
}