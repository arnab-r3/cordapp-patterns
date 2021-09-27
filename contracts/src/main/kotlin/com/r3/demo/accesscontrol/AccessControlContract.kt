package com.r3.demo.accesscontrol

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction


class AccessControlContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        TODO("Not yet implemented")
    }
}