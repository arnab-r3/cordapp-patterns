package com.r3.demo.stateencapsulation.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.stateencapsulation.contracts.EncapsulatedState
import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class PrivacyFlows(val value: String) : FlowLogic<Unit>() {

    private val PARTY_A = "O=PartyA,L=London,C=GB"
    private val PARTY_B = "O=PartyB,L=London,C=GB"
    private val PARTY_C = "O=PartyC,L=London,C=GB"
    private val PARTY_D = "O=PartyD,L=London,C=GB"

    @Suspendable
    override fun call() {
        val partyA = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(PARTY_A))!!
        val partyB = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(PARTY_B))!!
        val partyC = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(PARTY_C))!!
        val partyD = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(PARTY_D))!!

        val allSessions = listOf(partyB, partyC, partyD).map { initiateFlow(it) }
        val encapsulatedState = EncapsulatedState("$value, firstValue", listOf(partyA, partyB))
        val anotherEncapsulatedState = EncapsulatedState("$value, secondValue", listOf(partyC, partyD))
        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))

        txBuilder.addOutputState(encapsulatedState)
        txBuilder.addOutputState(anotherEncapsulatedState)
        txBuilder.addCommand(StateEncapsulationContract.Commands.TestPrivacyContract(), partyA.owningKey)

        val stx = serviceHub.signInitialTransaction(txBuilder)

        subFlow(FinalityFlow(stx, sessions = allSessions, statesToRecord = StatesToRecord.ONLY_RELEVANT))
    }
}

@InitiatedBy(PrivacyFlows::class)
class PrivacyFlowResponder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        subFlow(ReceiveFinalityFlow(otherSideSession = counterPartySession,
            statesToRecord = StatesToRecord.ONLY_RELEVANT))
    }

}

