package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction


/**
 * Flows that finalises the exchange of assets between two parties
 */
object InitiateExchangeFlows {

    @StartableByRPC
    @InitiatingFlow
    class Requestor : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            TODO("Not yet implemented")
        }

    }

}