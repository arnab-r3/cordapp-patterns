package com.r3.custom

import com.r3.custom.DataType.*
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import sun.security.x509.X500Name
import java.time.Duration
import java.time.Instant
import java.util.*

@CordaSerializable
class SchemaWithoutACD(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String?,
    val attributes: Set<AttributeWithoutACD>,
    val parties: List<X500Name> // defines the list of participants with whom the schema should be distributed
) {

    constructor(
        name: String,
        attributes: Set<AttributeWithoutACD>,
        parties: List<X500Name>
    ) : this(id = UUID.randomUUID(),
        name = name,
        description = null,
        attributes = attributes,
        parties = parties)
}

@CordaSerializable
data class AttributeWithoutACD(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String?,
    val dataType: DataType,
    val mandatory: Boolean = false,
    val regex: Regex?,
    val customValidator: (String?) -> Boolean = { true },
    val associatedEvents: Set<String>?
) {
    /**
     * Validate Mandatory
     * @param - the datum to validate
     * @throws IllegalArgumentException if validation fails
     */
    @Suppress
    fun validateMandatory(datum: String?) =
        require((mandatory && datum != null && datum.isNotBlank()) || !mandatory)
        { "Mandatory check failed for attribute $name" }


    /**
     * Validate Regex
     * @param - the datum to validate
     * @throws IllegalArgumentException if validation fails
     */
    @Suppress
    fun validateRegex(datum: String?) = require(datum?.let { regex?.matches(it) } == true)
    { "Regex validation failed for attribute $name, on value : $datum" }

    /**
     * Validate Data Type
     * @param - the datum to validate
     * @throws IllegalArgumentException if validation fails
     */
    @Suppress
    fun validateDataType(datum: String?) {
        datum?.let {
            try {
                when (dataType) {
                    BOOLEAN -> datum.toBoolean()
                    STRING -> datum as String
                    DURATION -> Duration.parse(datum)
                    BIG_DECIMAL -> datum.toBigDecimal()
                    INSTANT -> Instant.parse(datum)
                    INTEGER -> datum.toInt()
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Data type validation failed on attribute $name, expected data type: $dataType, has value $datum")
            }
        }
    }

    /**
     * Check all validations
     * @param datum the datum to be checked
     * @throws IllegalArgumentException if any validation fails
     */
    fun validate(datum: String?) {
        validateMandatory(datum)
        validateDataType(datum)
        validateRegex(datum)
        require(customValidator(datum)) { "Custom validation failed on attribute $name" }
    }
}

@CordaSerializable
@BelongsToContract(ExtensibleWorkflowContract::class)
data class SchemaWithoutACDBackedKV<T : Contract>(
    val id: UUID = UUID.randomUUID(),
    val kvPairs: Map<String, String>,
    val schema: SchemaWithoutACD,
    val participants: List<X500Name>  // defines the list of participants to whom this KV should be distributed
) {
    /**
     * Validate the KV against the backing schema
     * @param eventName the event name to validate the schema against, validate against all if null
     * @throws IllegalArgumentException if validation fails
     */
    fun validateSchema(eventName: String? = null, ledgerTransaction: LedgerTransaction?, serviceHub: ServiceHub?) {

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

