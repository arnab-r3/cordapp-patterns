package com.template.contracts

import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract
import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract.Commands.*
import com.r3.examples.testing.BaseContractTests
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests : BaseContractTests(StateEncapsulationContract.ID) {

    private val ledgerServices: MockServices = MockServices(listOf("com.r3.demo.stateencapsulation.contracts"))

    @Test
    fun `can issue encapsulating state`() {

        val encapsulated = getNewEncapsulatedState()
        val encapsulating = getNewEncapsulatingState(encapsulated.linearId.id)

        ledgerServices.ledger {
            transaction {
                input(encapsulating)
                command(getAllSignersPublicKeys(), CreateEncapsulating())
                fails()
            }
            transaction {
                output(encapsulating)
                command(getAllSignersPublicKeys(), CreateEncapsulating())
                verifies()
            }
            transaction {
                output(encapsulating)
                output(encapsulating)
                command(getAllSignersPublicKeys(), CreateEncapsulating())
                fails()
            }
        }
    }

    @Test
    fun `can issue encapsulated state`() {
        val encapsulated = getNewEncapsulatedState()
        ledgerServices.ledger {
            transaction {
                input(encapsulated)
                command(getAllSignersPublicKeys(), CreateEncapsulated())
                fails()
            }
            transaction {
                output(encapsulated)
                input(encapsulated)
                command(getAllSignersPublicKeys(), CreateEncapsulated())
                fails()
            }
            transaction {
                output(encapsulated)
                command(getAllSignersPublicKeys(), CreateEncapsulated())
                verifies()
            }
        }
    }

    @Test
    fun `can update encapsulated state`() {
        val encapsulated = getNewEncapsulatedState()
        val otherEncapsulated = getNewEncapsulatedState()

        ledgerServices.ledger {
            transaction {
                input(encapsulated)
                output(otherEncapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulated())
                fails()
            }
            transaction {
                output(encapsulated)
                input(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulated())
                verifies()
            }
            transaction {
                output(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulated())
                fails()
            }
            transaction {
                input(encapsulated)
                output(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulated())
                verifies()
            }
        }
    }

    @Test
    fun `can update encapsulating state`() {
        val encapsulated = getNewEncapsulatedState()
        val encapsulatingState = getNewEncapsulatingState(encapsulated.linearId.id)

        val otherEncapsulatedState = getNewEncapsulatedState()
        val otherEncapsulatingState = getNewEncapsulatingState(otherEncapsulatedState.linearId.id)

        ledgerServices.ledger {
            transaction {
                output(encapsulatingState)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                output(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                input(encapsulatingState)
                output(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                input(encapsulated)
                output(encapsulated)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                input(encapsulated)
                output(encapsulatingState)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                input(otherEncapsulatingState)
                output(encapsulatingState)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                fails()
            }
            transaction {
                input(encapsulatingState)
                output(encapsulatingState)
                command(getAllSignersPublicKeys(), UpdateEncapsulating())
                verifies()
            }
        }
    }
}