package com.r3.demo.extensibleworkflows

import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

object ConfigurableWorkflows {
    class Initiator : FlowLogic<SignedTransaction>() {
        override fun call(): SignedTransaction {
            TODO("Not yet implemented")
        }

    }

    class Responder: FlowLogic<SignedTransaction>() {
        override fun call(): SignedTransaction {
            TODO("Not yet implemented")
        }

    }
}