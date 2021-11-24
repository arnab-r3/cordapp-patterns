package com.r3.custom

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StaticPointer
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.util.*

@CordaSerializable
class SchemaState(
    val schema: Schema,
    override val participants: List<Party>
) : ContractState {

    val id: UUID
        get() = schema.id

    fun setParticipantsFromSchema(serviceHub: ServiceHub) =
        schema.parties.map {
            serviceHub.identityService.wellKnownPartyFromX500Name(it)
        }

}

/**
 * Create a state that contains an encapsulated [SchemaState] within it. The intention is to include it
 * whenever we need to store arbitrary dynamically defined data within a state.
 * The encapsulating state containing the [SchemaState] can decide how to use the schema to validate the
 * dynamically defined data and also how to maintain its lifecycle (CRUD). Here, we are using a Key-Value based
 * data storage and defining our own logic to store and update data using KV pairs. We call the convenience validation
 * methods inside the [Schema] to define our validate() method that validates the KV based data.
 */
@CordaSerializable
@BelongsToContract(SchemaBackedKVContract::class)
data class SchemaBackedKVState(
    val id: UUID = UUID.randomUUID(),
    val kvPairs: Map<String, String>,
    val schemaStatePointer: StaticPointer<SchemaState>,
    override val participants: List<AbstractParty>
    ) : ContractState{

    /**
     * Validate the KV against the backing schema
     * @param eventName the event name to validate the schema against, validate against all if null
     * @throws IllegalArgumentException if validation fails
     */
    @Suppress("unused")
    fun validateSchema(eventName: String? = null, ledgerTransaction: LedgerTransaction?, serviceHub: ServiceHub?) {


        val schemaStateAndRef = ledgerTransaction?.let { schemaStatePointer.resolve(ledgerTransaction) }?:
        serviceHub?.let { schemaStatePointer.resolve(serviceHub) }?:
        throw java.lang.IllegalArgumentException("At least one of ledger transaction or service hub reference should be present while validating schema")

        val schema = schemaStateAndRef.state.data.schema

        kvPairs.keys.forEach {
            require(it in schema.attributes.map { attr -> attr.name })
            { "Unknown key $it present in schema ${schema.name}" }
        }
        // validate attributes based on the event triggered
        schema.attributes.filter { attribute ->
            eventName?.let { eventName ->
                attribute.associatedEvents?.contains(eventName)
            } ?: true
        }.forEach {
            it.validate(kvPairs[it.name])
        }
    }
}