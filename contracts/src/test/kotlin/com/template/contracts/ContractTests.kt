package com.template.contracts

import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract
import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract.Commands.*
import com.r3.examples.testing.BaseContractTests
import com.r3.examples.testing.Identities
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests : BaseContractTests() {
    private val ledgerServices: MockServices = MockServices(listOf("com.r3.demo.stateencapsulation.contracts"))


    @Test
    fun `can issue encapsulating state`() {

        val encapsulated = getNewEncapsulatedState()
        val encapsulating = getNewEncapsulatingState(encapsulated.linearId.id)

        ledgerServices.ledger {
            transaction {
                input(StateEncapsulationContract.ID, encapsulating)
                command(getAllSignersPublicKeys(), CreateEncapsulating())
                fails()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulating)
                command(getAllSignersPublicKeys(), CreateEncapsulating())
                verifies()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulating)
                output(StateEncapsulationContract.ID, encapsulating)
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
                input(StateEncapsulationContract.ID, encapsulated)
                command(getAllSignersPublicKeys(), CreateEnclosed())
                fails()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulated)
                input(StateEncapsulationContract.ID, encapsulated)
                command(getAllSignersPublicKeys(), CreateEnclosed())
                fails()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulated)
                command(getAllSignersPublicKeys(), CreateEnclosed())
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
                input(StateEncapsulationContract.ID, encapsulated)
                output(StateEncapsulationContract.ID, otherEncapsulated)
                command(getAllSignersPublicKeys(), UpdateEnclosed())
                fails()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulated)
                input(StateEncapsulationContract.ID, encapsulated)
                command(getAllSignersPublicKeys(), UpdateEnclosed())
                verifies()
            }
            transaction {
                output(StateEncapsulationContract.ID, encapsulated)
                command(listOf(Identities.BOB.publicKey), UpdateEnclosed())
                fails()
            }
            transaction {
                input(StateEncapsulationContract.ID, encapsulated)
                output(StateEncapsulationContract.ID, encapsulated)
                command(listOf(Identities.BOB.publicKey), UpdateEnclosed())
                verifies()
            }
        }
    }
}