package com.r3.demo.contractdependency.contracts

import com.r3.demo.contractdependency.states.BloomFilterState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class BloomFilterContract: Contract {
    override fun verify(tx: LedgerTransaction) {
        val outputsOfType = tx.outputsOfType<BloomFilterState>()
        val checkContains = outputsOfType.single().checkContains("test")
        println("checkContains: $checkContains")
    }

    interface BloomFilterCommands : CommandData {
        class CreateFilter : BloomFilterCommands
        class UpdateFilter : BloomFilterCommands
    }
}