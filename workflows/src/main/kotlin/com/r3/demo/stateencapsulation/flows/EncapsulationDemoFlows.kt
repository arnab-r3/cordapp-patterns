package com.r3.demo.stateencapsulation.flows

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.stateencapsulation.contracts.EncapsulatedState
import com.r3.demo.stateencapsulation.contracts.EncapsulatingState
import com.r3.demo.stateencapsulation.contracts.StateEncapsulationContract
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

fun fail(message: String): Nothing = throw IllegalArgumentException(message)



object EncapsulationDemoFlows {


    @StartableByRPC
    @InitiatingFlow
    class InitiatorFlow(
        private val commandString: String,
        private val counterParty: Party,
        private val txObject: ExampleTransactionObject
    ) : FlowLogic<String>() {


        companion object {
            object SET_UP : ProgressTracker.Step("Initialising flows.")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            fun tracker() = ProgressTracker(SET_UP, BUILDING_THE_TX,
                VERIFYING_THE_TX, COLLECTING_SIGS_AND_FINALITY)
        }

        override val progressTracker = tracker()

        @JsonIgnoreProperties(ignoreUnknown = true)
        @CordaSerializable
        data class ExampleTransactionObject(
            val enclosingValue: String? = null,
            val enclosedValue: String? = null,
            val outerIdentifier: UUID? = null,
            val innerIdentifier: UUID? = null
        )

        @Suspendable
        private fun createTransaction()
                : Pair<TransactionBuilder, String> {

            return when (commandString) {
                "CreateEncapsulating" -> {

                    if (txObject.innerIdentifier != null && txObject.enclosingValue != null) {

                        // check if the inner value id exists
                        checkStateWithIdExists(EncapsulatedState::class.java, txObject.innerIdentifier)

                        val encapsulatingState =
                            EncapsulatingState(txObject.enclosingValue,
                                txObject.innerIdentifier,
                                listOf(ourIdentity, counterParty))

                        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatingState)
                            .addCommand(StateEncapsulationContract.Commands.CreateEncapsulating(),
                                listOf(ourIdentity.owningKey, counterParty.owningKey))

                        txBuilder to "Encapsulating created, ID: ${encapsulatingState.linearId.id}"

                    } else fail("Create Encapsulating state must accompany the inner identifier and the outer enclosing value")

                }
                "UpdateEncapsulating" -> {

                    if (txObject.outerIdentifier != null && txObject.innerIdentifier != null && txObject.enclosingValue != null) {

                        // check if the inner and outer value id exists
                        checkStateWithIdExists(EncapsulatedState::class.java, txObject.innerIdentifier)
                        checkStateWithIdExists(EncapsulatingState::class.java, txObject.outerIdentifier)

                        val queriedEncapsulatingState =
                            serviceHub
                                .vaultService
                                .queryBy<EncapsulatingState>()
                                .states
                                .single { it.state.data.linearId.id == txObject.outerIdentifier }

                        val encapsulatingState =
                            queriedEncapsulatingState
                                .state
                                .data
                                .withNewValues(txObject.enclosingValue,txObject.innerIdentifier)


                        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatingState)
                            .addInputState(queriedEncapsulatingState)
                            .addCommand(StateEncapsulationContract.Commands.UpdateEncapsulating(),
                                listOf(ourIdentity.owningKey, counterParty.owningKey))

                        txBuilder to
                                "Encapsulating updated with inner identifier: ${txObject.innerIdentifier} and outer identifier: ${txObject.outerIdentifier}"

                    } else fail("Update Encapsulating state must accompany the inner and outer identifiers and the outer enclosing value")

                }
                "CreateEncapsulated" -> {

                    if (txObject.enclosedValue != null) {
                        val encapsulatedState =
                            EncapsulatedState(txObject.enclosedValue, listOf(ourIdentity, counterParty))

                        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatedState)
                            .addCommand(StateEncapsulationContract.Commands.CreateEncapsulated(),
                                listOf(ourIdentity.owningKey, counterParty.owningKey))

                        txBuilder to "Encapsulated created with identifier ${encapsulatedState.linearId.id}"

                    } else fail("Create of Encapsulated state should include the enclosing value")

                }
                "UpdateEncapsulated" -> {
                    if (txObject.enclosedValue != null && txObject.innerIdentifier != null) {

                        // check if the inner value id exists
                        checkStateWithIdExists(EncapsulatedState::class.java, txObject.innerIdentifier)

                        val queriedEncapsulatedState =
                            serviceHub
                                .vaultService
                                .queryBy<EncapsulatedState>()
                                .states
                                .single { it.state.data.linearId.id == txObject.innerIdentifier }

                        val encapsulatedState =
                            queriedEncapsulatedState
                                .state
                                .data
                                .withNewValue(txObject.enclosedValue)

                        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                            .addInputState(queriedEncapsulatedState)
                            .addOutputState(encapsulatedState)
                            .addCommand(StateEncapsulationContract.Commands.UpdateEncapsulated(),
                                listOf(ourIdentity.owningKey, counterParty.owningKey))

                        txBuilder to "Encapsulated Updated with id: ${txObject.innerIdentifier}".trimIndent()

                    } else fail("Update of Encapsulated state should include the enclosing value and the state identifier to be updated")
                }
                else -> fail("Invalid Command")
            }
        }

        // check if the provided id exists
        private inline fun <reified T : ContractState> checkStateWithIdExists(type: Class<T>, identifier: UUID) {
            val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(
                uuid = listOf(identifier),
                contractStateTypes = setOf(type))
            require(serviceHub.vaultService.queryBy<T>(linearStateQueryCriteria).states.isNotEmpty()
            ) { "Provided $identifier do not correspond to any matching states of type $type" }
        }

        @Suspendable
        override fun call(): String {

            progressTracker.currentStep = BUILDING_THE_TX

            val (txBuilder, txOutputString) = createTransaction()

            progressTracker.currentStep = VERIFYING_THE_TX

            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY

            val subFlow: SignedTransaction = subFlow(
                CollectSignaturesAndFinalizeTransactionFlow(
                    txBuilder,
                    null,
                    listOf(counterParty),
                    listOf(counterParty)))

            return "$txOutputString, Tx ID: ${subFlow.id}"

        }

    }

//    @InitiatedBy(InitiatorFlow::class)
//    class ResponderFlow(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
//
//        @Suspendable
//        override fun call() {
//            // we have nothing to do here...
//        }
//
//    }
}