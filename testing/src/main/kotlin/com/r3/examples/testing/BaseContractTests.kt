package com.r3.examples.testing

import com.template.states.EncapsulatedState
import com.template.states.EncapsulatingState
import net.corda.core.contracts.ContractState
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import java.util.*

abstract class BaseContractTests(private val defaultContractID: String) {

    protected fun getNewEncapsulatedState() =
        EncapsulatedState("INNER_VALUE", listOf(Identities.ALICE.party, Identities.BOB.party))

    protected fun getNewEncapsulatingState(innerIdentifier: UUID) =
        EncapsulatingState("OUTER_VALUE", innerIdentifier, listOf(Identities.ALICE.party, Identities.BOB.party))

    protected fun getAllSignersPublicKeys() = listOf(Identities.ALICE.publicKey, Identities.BOB.publicKey)

    protected fun TransactionDSL<TransactionDSLInterpreter>.input(contractState: ContractState) {
        input(defaultContractID, contractState)
    }

    protected fun TransactionDSL<TransactionDSLInterpreter>.output(contractState: ContractState) {
        output(defaultContractID, contractState)
    }

}