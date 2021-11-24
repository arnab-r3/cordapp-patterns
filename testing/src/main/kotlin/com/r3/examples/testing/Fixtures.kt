package com.r3.examples.testing

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.StaticPointer
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity

object Identities {
    val ALICE = TestIdentity(CordaX500Name.parse("O=Alice,L=London,C=GB"))
    val BOB = TestIdentity(CordaX500Name.parse("O=Bob,L=Mumbai,C=IN"))

}

inline fun <reified T: ContractState> staticPointer() = StaticPointer(StateRef(SecureHash.zeroHash,0),T::class.java)