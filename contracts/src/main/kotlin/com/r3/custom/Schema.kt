package com.r3.custom

import com.r3.custom.DataType.*
import net.corda.core.serialization.CordaSerializable
import sun.security.x509.X500Name
import java.time.Duration
import java.time.Instant
import java.util.*


@CordaSerializable
enum class DataType {
    BOOLEAN,
    INTEGER,
    BIG_DECIMAL,
    STRING,
    INSTANT,
    DURATION
}

@CordaSerializable
data class Schema(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val version: String,
    val description: String?,
    val attributes: Set<Attribute>,
    val parties: List<X500Name> // defines the list of participants with whom the schema should be distributed
)


@CordaSerializable
data class Attribute(
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
    @Suppress("IMPLICIT_CAST_TO_ANY")
    fun validateDataType(datum: String?) {
        datum?.let {
            try {
                when (dataType) {
                    BOOLEAN -> datum.toBoolean()
                    STRING -> datum
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


