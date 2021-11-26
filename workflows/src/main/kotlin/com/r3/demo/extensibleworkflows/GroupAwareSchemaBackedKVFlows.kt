package com.r3.demo.extensibleworkflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.custom.SchemaBackedKVContract
import com.r3.custom.SchemaBackedKVState
import com.r3.custom.SchemaState
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.datadistribution.flows.GroupDataManagementFlow
import com.r3.demo.datadistribution.flows.MembershipBroadcastFlows
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getDefaultNotary
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StaticPointer
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object ManageGroupAwareSchemaBackedKVData {

    @CordaSerializable
    enum class Operation {
        CREATE, UPDATE
    }

    /**
     * Manage Group Aware Schema based KV data - CREATE and UPDATE, can add more too
     * @param groupDataAssociationStateIdentifier that links the schema with the groups
     * @param schemaId the schema identifier committed alongside with the [GroupDataAssociationState]
     * @param data in key-value pairs of [String] type
     * @param operation to be performed, either of [Operation.CREATE] or [Operation.UPDATE]
     * @param schemaBackedKVId required optionally only during [Operation.UPDATE]
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val groupDataAssociationStateIdentifier: String,
        private val schemaBackedKVId: String? = null,
        private val schemaId: String,
        private val data: Map<String, String>,
        private val operation: Operation
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
            object VALIDATING_SCHEMA_LINKAGE : ProgressTracker.Step("Validating Schema Linkage with Group Data")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_TX : ProgressTracker.Step("Verifying transaction")
            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker =
                    MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
            }

            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                VALIDATING_SCHEMA_LINKAGE,
                BUILDING_THE_TX,
                VERIFYING_TX,
                COLLECTING_SIGS_AND_FINALITY,
                DISTRIBUTING_GROUP_DATA)
        }

        override val progressTracker = tracker()


        private fun getSchemaBackedKVState(schemaBackedKVId: String): StateAndRef<SchemaBackedKVState> {
            val vaultQueryCriteria = QueryCriteria
                .VaultQueryCriteria()
                .withContractStateTypes(setOf(SchemaBackedKVState::class.java))
                .withRelevancyStatus(Vault.RelevancyStatus.ALL)
                .withStatus(Vault.StateStatus.UNCONSUMED)


            val queryResult = serviceHub
                .vaultService
                .queryBy<SchemaBackedKVState>(vaultQueryCriteria)
                .states.filter { it.state.data.id.toString() == schemaBackedKVId }

            require(queryResult.size == 1) { "Could not find SchemaBackedKVState with id: $schemaBackedKVId" }
            return queryResult.single()
        }

        @Suspendable
        override fun call(): String {

            // check existence of GroupDataAssociationState & the existence of the schema id committed in the same tx

            progressTracker.currentStep = FETCHING_GROUP_DETAILS
            val groupDataState = getGroupDataState(groupDataAssociationStateIdentifier)

            // find the schema state committed with the transaction
            progressTracker.currentStep = VALIDATING_SCHEMA_LINKAGE
            val referredSchemaState = getGroupDataAssociatedStates(groupDataAssociationStateIdentifier)
            {
                it is SchemaState && it.schema.id.toString() == schemaId
            }?.single() ?: flowFail("Cannot find a schema with id: $schemaId committed " +
                    "in a transaction with GroupDataAssociationState with id $groupDataAssociationStateIdentifier")

            progressTracker.currentStep = BUILDING_THE_TX
            val participants = groupDataState.state.data.participants
            val signingKeys = groupDataState.state.data.participants.map { it.owningKey }

            var returnedSchemaBackedKVStateId = ""

            val transactionBuilder = when (operation) {
                Operation.CREATE -> {
                    val outputState = SchemaBackedKVState(
                        kvPairs = data,
                        schemaStatePointer = StaticPointer(referredSchemaState.ref, SchemaState::class.java),
                        participants = participants // we can also distribute to a smaller audience
                    )

                    returnedSchemaBackedKVStateId = outputState.id.toString()

                    TransactionBuilder(getDefaultNotary(serviceHub))
                        .addCommand(SchemaBackedKVContract.Commands.CreateData(), signingKeys)
                        .addOutputState(outputState)
                        .addReferenceState(referredSchemaState.referenced())
                }
                Operation.UPDATE -> {
                    schemaBackedKVId?.let { schemaBackedKVId ->

                        val inputState = getSchemaBackedKVState(schemaBackedKVId)
                        val outputState = inputState.state.data.copy(
                            kvPairs = data
                        )

                        returnedSchemaBackedKVStateId = outputState.id.toString()

                        TransactionBuilder(getDefaultNotary(serviceHub))
                            .addCommand(SchemaBackedKVContract.Commands.UpdateData(), signingKeys)
                            .addInputState(inputState)
                            .addOutputState(outputState)
                            .addReferenceState(referredSchemaState.referenced())
                    } ?: flowFail("Update operation must include the schema backed KV id to be updated")
                }
            }

            progressTracker.currentStep = VERIFYING_TX
            transactionBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = transactionBuilder,
                signers = participants.toSet(),
                participants = participants.toSet()))

            return "Performed ${operation.name} on Schema-backed KV with identifier $returnedSchemaBackedKVStateId " +
                    "using schema id: $schemaId, " +
                    "linked with GroupDataAssociation id: $groupDataAssociationStateIdentifier"

        }

    }

}