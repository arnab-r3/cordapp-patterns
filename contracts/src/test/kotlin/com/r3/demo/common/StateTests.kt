package com.r3.demo.common

import com.r3.demo.stateencapsulation.contracts.EncapsulatedState
import com.r3.demo.stateencapsulation.contracts.EncapsulatingState
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals

class StateTests {


    @Test
    fun hasFieldsOfCorrectTypeInEncapsulatedState() {

        val innerValue = EncapsulatedState::class.java.getDeclaredField("innerValue")
        val participants = EncapsulatedState::class.java.getDeclaredField("participants")
        val linearId = EncapsulatedState::class.java.getDeclaredField("linearId")

        assertEquals(innerValue.type, String::class.java)
        assertEquals(participants.type, List::class.java)
        assertEquals(linearId.type, UniqueIdentifier::class.java)

    }

    @Test
    fun hasFieldsOfCorrectTypeInEncapsulatingState() {

        val outerValue = EncapsulatingState::class.java.getDeclaredField("outerValue")
        val encapsulatedStateIdentifier = EncapsulatingState::class.java.getDeclaredField("encapsulatedStateIdentifier")
        val participants = EncapsulatedState::class.java.getDeclaredField("participants")
        val linearId = EncapsulatingState::class.java.getDeclaredField("linearId")

        assertEquals(outerValue.type, String::class.java)
        assertEquals(participants.type, List::class.java)
        assertEquals(linearId.type, UniqueIdentifier::class.java)
        assertEquals(encapsulatedStateIdentifier.type, LinearPointer::class.java)

    }
}