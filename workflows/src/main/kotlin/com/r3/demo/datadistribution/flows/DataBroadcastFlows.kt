package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction


object DataBroadCastFlows {

    // reference: https://lankydan.dev/broadcasting-a-transaction-to-external-organisations
    @StartableByService
    @InitiatingFlow
    class TransactionBroadcastInitiatorFlow(
        private val signedTransaction: SignedTransaction,
        private val counterParty: Party
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val flowSession = initiateFlow(counterParty)
            subFlow(SendTransactionFlow(flowSession, signedTransaction))
//            subFlow(SendStateAndRefFlow)
        }
    }


    @InitiatedBy(TransactionBroadcastInitiatorFlow::class)
    class TransactionBroadcastResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))

            // TODO add logic if we are entitled to distribute then distribute
        }
    }


    @StartableByService
    @InitiatingFlow
    class StateAndRefBroadcastInitiatorFlow(
        private val stateAndRefs: Set<StateAndRef<ContractState>>,
        private val counterParty: Party
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val flowSession = initiateFlow(counterParty)
            subFlow(SendStateAndRefFlow(flowSession, stateAndRefs.toList()))
        }
    }


    @InitiatedBy(StateAndRefBroadcastInitiatorFlow::class)
    class StateAndRefBroadcastResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveStateAndRefFlow<ContractState>(session))
            // TODO add logic if we are entitled to distribute then distribute
        }
    }

}


