package com.r3.examples.testing

import com.template.states.EncapsulatedState
import com.template.states.EncapsulatingState
import java.util.*

abstract class BaseContractTests {

    protected fun getNewEncapsulatedState() =
        EncapsulatedState("INNER_VALUE", listOf(Identities.ALICE.party, Identities.BOB.party))

    protected fun getNewEncapsulatingState(innerIdentifier: UUID) =
        EncapsulatingState("OUTER_VALUE", innerIdentifier, listOf(Identities.ALICE.party, Identities.BOB.party))

    protected fun getAllSignersPublicKeys() = listOf(Identities.ALICE.publicKey, Identities.BOB.publicKey)


}