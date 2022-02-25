package com.r3.demo.contractdependency.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class BloomFilterContract: Contract {
    override fun verify(tx: LedgerTransaction) {

    }

    interface BloomFilterCommands : CommandData {
        class CreateFilter : BloomFilterCommands
        class UpdateFilter : BloomFilterCommands
    }
}