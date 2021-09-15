package com.r3.demo.datadistribution.flows

import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@CordaService
class DistributionService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {


    private class CallDistributeFlow(val serviceHub: AppServiceHub, val receiver: Party, val signedTransaction: SignedTransaction) : Callable<Unit> {
        override fun call(): Unit =
            serviceHub.startFlow(DataBroadCastFlows.InitiatorFlow(signedTransaction, receiver)).returnValue.get()
    }

    fun distributeTransactionParallel (signedTransaction: SignedTransaction, parties: Set<Party>) {
        val executorService = Executors.newFixedThreadPool(20)
        for (party in parties){
            executorService.submit(CallDistributeFlow(appServiceHub, party, signedTransaction))
        }
    }

}