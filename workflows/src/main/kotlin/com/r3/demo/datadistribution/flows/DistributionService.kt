package com.r3.demo.datadistribution.flows

import net.corda.core.concurrent.CordaFuture
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


    private lateinit var executorService : ExecutorService

    private class CallDistributeFlow(val serviceHub: AppServiceHub, val receiver: Party, val signedTransaction: SignedTransaction) : Callable<CordaFuture<Unit>> {
        override fun call(): CordaFuture<Unit> =
            serviceHub.startFlow(DataBroadCastFlows.InitiatorFlow(signedTransaction, receiver)).returnValue
    }

    fun distributeTransactionParallel (signedTransaction: SignedTransaction, parties: Set<Party>) {
        if (!this::executorService.isInitialized) {
            executorService = Executors.newFixedThreadPool(MAX_THREAD_SIZE)
        }
        for (party in parties){
            executorService.submit(CallDistributeFlow(appServiceHub, party, signedTransaction))
        }
    }
}