package com.r3.demo.crossnotaryswap.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

class RevertIntentFlow(private val offeredEncumberedTx: SignedTransaction): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

    }

}