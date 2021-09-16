package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction


object DataBroadCastFlows {

    // reference: https://lankydan.dev/broadcasting-a-transaction-to-external-organisations
    @StartableByRPC
    @InitiatingFlow
    class InitiatorFlow(
        private val signedTransaction: SignedTransaction,
        private val counterParty: Party
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val flowSession = initiateFlow(counterParty)
            subFlow(SendTransactionFlow(flowSession, signedTransaction))
        }
    }


    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }

}


