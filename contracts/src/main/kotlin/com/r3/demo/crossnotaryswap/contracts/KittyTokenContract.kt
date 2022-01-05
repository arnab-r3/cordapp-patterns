package com.r3.demo.crossnotaryswap.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.demo.crossnotaryswap.states.KittyToken
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class KittyTokenContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        requireThat {
            "There must be no input states" using tx.inputStates.isEmpty()
            "There must be a single output state of type KittyToken" using (tx.outputStates.size == 1 && tx.outputStates.single() is KittyToken)
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val oldKitty = tx.inputStates.single() as KittyToken
        val newKitty = tx.outputStates.single() as KittyToken
        requireThat {
            "Fraction Digits cannot change" using (oldKitty.fractionDigits == newKitty.fractionDigits)
        }
    }
}