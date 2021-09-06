package com.r3.example.flows

import com.r3.utils.ExampleContract
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import com.template.states.EncapsulatedState
import com.template.states.EncapsulatingState
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

fun fail(message: String): Nothing = throw IllegalArgumentException(message)

fun getDefaultNotary(serviceHub: ServiceHub) = serviceHub.networkMapCache.notaryIdentities.first()

object ExampleFlows {


    @InitiatingFlow
    class InitiatorFlow(
        private val commandString: String,
        private val counterParty: Party,
        private val txObject: ExampleTransactionObject
    ) : FlowLogic<SignedTransaction>() {


        companion object {
            object SET_UP : ProgressTracker.Step("Initialising flows.")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object WE_SIGN : ProgressTracker.Step("signing transaction.")
            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(SET_UP, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, FINALISING)
        }

        override val progressTracker = tracker()

        data class ExampleTransactionObject(
            val enclosingValue: String?,
            val enclosedValue: String?,
            val outerIdentifier: UUID?,
            val innerIdentifier: UUID?
        )

        private fun createTransaction()
                : TransactionBuilder {

            val command =
                Class.forName("com.r3.utils.ExampleContract.Commands.$commandString")
                    .newInstance() as ExampleContract.Commands

            return when (command) {
                is ExampleContract.Commands.CreateEncapsulating -> {

                    if (txObject.innerIdentifier != null && txObject.enclosingValue != null) {
                        val encapsulatingState =
                            EncapsulatingState(
                                txObject.enclosingValue,
                                UniqueIdentifier(),
                                LinearPointer(
                                    UniqueIdentifier(null, txObject.innerIdentifier),
                                    EncapsulatedState::class.java,
                                    false),
                                listOf(ourIdentity, counterParty))

                        progressTracker.currentStep =
                            ProgressTracker.Step("Creating Transaction : Create \"Encapsulating\" with outer identifier: ${encapsulatingState.identifier.id}")

                        TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatingState)
                            .addCommand(ExampleContract.Commands.CreateEncapsulating())

                    } else fail("Create Encapsulating state must accompany the inner identifier and the outer enclosing value")

                }
                is ExampleContract.Commands.UpdateEncapsulating -> {

                    if (txObject.outerIdentifier != null && txObject.innerIdentifier != null && txObject.enclosingValue != null) {

                        val queriedEncapsulatingState =
                            serviceHub.vaultService.queryBy<EncapsulatingState>().states.single { it.state.data.identifier.id == txObject.outerIdentifier }

                        val encapsulatingState =
                            EncapsulatingState(
                                txObject.enclosingValue,
                                UniqueIdentifier(id = txObject.outerIdentifier),
                                LinearPointer(
                                    UniqueIdentifier(null, txObject.innerIdentifier),
                                    EncapsulatedState::class.java,
                                    false),
                                listOf(ourIdentity, counterParty))

                        progressTracker.currentStep =
                            ProgressTracker.Step("Creating transaction : Update \"Encapsulating\" with outer identifier: ${encapsulatingState.identifier.id}")

                        TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatingState)
                            .addInputState(queriedEncapsulatingState)
                            .addCommand(ExampleContract.Commands.UpdateEncapsulating())
                    } else fail("Update Encapsulating state must accompany the inner and outer identifiers and the outer enclosing value")

                }
                is ExampleContract.Commands.CreateEnclosed -> {

                    if (txObject.enclosedValue != null) {
                        val encapsulatedState =
                            EncapsulatedState(txObject.enclosedValue, listOf(ourIdentity, counterParty))

                        progressTracker.currentStep =
                            ProgressTracker.Step("Creating transaction : Create \"Encapsulated\" with identifier: ${encapsulatedState.linearId.id}")

                        TransactionBuilder(getDefaultNotary(serviceHub))
                            .addOutputState(encapsulatedState)

                    } else fail("Create of Encapsulated state should include the enclosing value")

                }
                is ExampleContract.Commands.UpdateEnclosed -> {
                    if (txObject.enclosingValue != null && txObject.innerIdentifier != null) {

                        val queriedEncapsulatedState =
                            serviceHub.vaultService.queryBy<EncapsulatedState>().states.single { it.state.data.linearId.id == txObject.innerIdentifier }

                        val encapsulatedState = EncapsulatedState(
                            txObject.enclosingValue,
                            listOf(ourIdentity, counterParty),
                            UniqueIdentifier(id = txObject.innerIdentifier)
                        )

                        progressTracker.currentStep =
                            ProgressTracker.Step("Creating transaction : Update \"Encapsulated\" with identifier: ${encapsulatedState.linearId.id}")

                        TransactionBuilder(getDefaultNotary(serviceHub))
                            .addInputState(queriedEncapsulatedState)
                            .addOutputState(encapsulatedState)

                    } else fail("Update of Encapsulated state should include the enclosing value and the state identifier to be updated")
                }
                else -> fail("Invalid Command")
            }
        }

        override fun call(): SignedTransaction {

            progressTracker.currentStep = BUILDING_THE_TX

            val txBuilder = createTransaction()

            progressTracker.currentStep = VERIFYING_THE_TX

            txBuilder.verify(serviceHub)

            progressTracker.currentStep = WE_SIGN
            val selfSignedTransaction = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING

            return subFlow(
                CollectSignaturesAndFinalizeTransactionFlow(
                    selfSignedTransaction,
                    progressTracker,
                    null,
                    setOf(counterParty),
                    setOf(counterParty, ourIdentity)))
        }

    }

    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow : FlowLogic<Unit>() {
        override fun call() {

        }

    }
}