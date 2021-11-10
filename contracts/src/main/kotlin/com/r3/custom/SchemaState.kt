package com.r3.custom

import com.r3.custom.DataType.*
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * Couple of observations
 * 1. Using the generic T on the SchemaState binds it tightly to the ContractAPI and cannot be used in the RPC MW
 * 2. Using the access control permissions unnecessarily complicates the API to depend on the generic type T
 * 3. The APIs also have heavy dependency on Corda Specific terms like [ContractState] and [LinearState] which couples it even further to the Corda Contract APIs
 * 4. There are some instances where you cannot avoid this coupling. Better to use inheritance to define non-Corda specific API and then extend it to use Corda Specific terms.
 */

@CordaSerializable
interface PermissionCommandData<T : Contract> : CommandData {

    /**
     * Least privilege granted, to be overridden by the class implementing this interface
     */
    fun doesUpdate(): Boolean = false

    /**
     * Least privilege granted, to be overridden by the class implementing this interface
     */
    fun doesCreate(): Boolean = false

    /**
     * Least privilege granted, to be overridden by the class implementing this interface
     */
    fun doesDelete(): Boolean = false

    /**
     * Check permissions to execute a command w.r.t to a given party
     * @param schemaState to validate against
     * @param requestingParty the party invoking the command
     */
    fun checkPermissions(schemaState: SchemaState<T>, requestingParty: Party) =
        schemaState.attributes.forEach {
            it.associatedEvents?.filter { filterCandidate ->
                filterCandidate.triggeringCommand.isInstance(this)
            }?.forEach { event ->
                require(event.checkPermission(requestingParty, this)) {
                    "Insufficient privileges for $requestingParty on schema ${schemaState.name} when invoking command $this"
                }
            }
        }
}

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
class SchemaState<T : Contract>(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String?,
    val attributes: Set<Attribute<T>>,
    override val participants: List<AbstractParty> // defines the list of participants with whom the schema should be distributed
) : ContractState {

    constructor(
        name: String,
        attributes: Set<Attribute<T>>,
        participants: List<AbstractParty>
    ) : this(id = UUID.randomUUID(),
        name = name,
        description = null,
        attributes = attributes,
        participants = participants)
}

@CordaSerializable
data class Attribute<T : Contract>(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String?,
    val dataType: DataType,
    val mandatory: Boolean = false,
    val regex: Regex?,
    val customValidator: (String?) -> Boolean = { true },
    val associatedEvents: Set<EventDescriptor<T>>?
) {
    constructor(name: String, dataType: DataType, mandatory: Boolean = false) : this(
        id = UUID.randomUUID(),
        name = name,
        description = null,
        dataType = dataType,
        mandatory = mandatory,
        regex = null,
        customValidator = { true },
        associatedEvents = null)

    constructor(
        name: String,
        dataType: DataType,
        mandatory: Boolean = false,
        associatedEvents: Set<EventDescriptor<T>>?
    ) : this(
        id = UUID.randomUUID(),
        name = name,
        description = null,
        dataType = dataType,
        mandatory = mandatory,
        regex = null,
        customValidator = { true },
        associatedEvents = associatedEvents)

    constructor(
        name: String,
        dataType: DataType,
        mandatory: Boolean = false,
        regex: Regex?,
        associatedEvents: Set<EventDescriptor<T>>?
    ) : this(
        id = UUID.randomUUID(),
        name = name,
        description = null,
        dataType = dataType,
        mandatory = mandatory,
        regex = regex,
        customValidator = { true },
        associatedEvents = associatedEvents)


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
data class EventDescriptor<T : Contract>(
    val name: String,
    val description: String?,
    val triggeringContract: KClass<T>,
    val triggeringCommand: KClass<out PermissionCommandData<T>>,
    val accessControlDefinition: AccessControlDefinition
) {
    init {
        validatePermissionsAgainstCommandDefinitions()
    }

    private fun validatePermissionsAgainstCommandDefinitions() {
        val instance = triggeringCommand.createInstance()
        require(!(!instance.doesCreate() && accessControlDefinition.canCreate)
                || (!instance.doesUpdate() && accessControlDefinition.canUpdate)
                || (!instance.doesDelete() && accessControlDefinition.canDelete))
        {
            "Assigned permissions cannot be higher than contract command definitions.\n" +
                    "operation: $triggeringCommand, doesCreate: ${instance.doesCreate()}, doesUpdate: ${instance.doesUpdate()}, doesDelete: ${instance.doesDelete()}\n" +
                    "assigned for event name: $name: canCreate: ${accessControlDefinition.canCreate}, canUpdate: ${accessControlDefinition.canUpdate}, canDelete: ${accessControlDefinition.canDelete}"
        }
    }

    private fun checkPermissionAgainstDefinition(
        accessControlDefinition: AccessControlDefinition,
        permissionCommandData: PermissionCommandData<T>
    ) =
        accessControlDefinition.canCreate == permissionCommandData.doesCreate()
                && accessControlDefinition.canDelete == permissionCommandData.doesDelete()
                && accessControlDefinition.canUpdate == permissionCommandData.doesUpdate()

    /**
     * Check permission to perform an operation by the requesting party
     * @param requestingParty the subject party
     * @param permissionCommandData the command the subject tries to invoke
     */
    internal fun checkPermission(requestingParty: Party, permissionCommandData: PermissionCommandData<T>) =
        accessControlDefinition.parties.any {
            it == requestingParty &&
                    checkPermissionAgainstDefinition(accessControlDefinition, permissionCommandData)
        }
}


@CordaSerializable
data class AccessControlDefinition(
    val parties: Set<AbstractParty>,
    val canCreate: Boolean = false,
    val canUpdate: Boolean = false,
    val canDelete: Boolean = false
)

@CordaSerializable
@BelongsToContract(ExtensibleWorkflowContract::class)
data class SchemaBackedKV<T : Contract>(
    val id: UUID = UUID.randomUUID(),
    val kvPairs: Map<String, String>,
    val schemaStatePointer: StatePointer<SchemaState<T>>,
    override val participants: List<AbstractParty>  // defines the list of participants to whom this KV should be distributed
) : ContractState {
    /**
     * Validate the KV against the backing schema
     * @param permissionCommand the command invocation to validate the schema against, validate against all if null
     * @throws IllegalArgumentException if validation fails
     */
    fun validateSchema(permissionCommand: KClass<out PermissionCommandData<T>>? = null, ledgerTransaction: LedgerTransaction?, serviceHub: ServiceHub?) {

        val schemaStateAndRef = ledgerTransaction?.let { schemaStatePointer.resolve(ledgerTransaction) }?:
        serviceHub?.let { schemaStatePointer.resolve(serviceHub) }?:
        throw java.lang.IllegalArgumentException("At least one of ledger transaction or service hub reference should be present while validating schema")

        val schema = schemaStateAndRef.state.data

        kvPairs.keys.forEach {
            require(it in schema.attributes.map { attr -> attr.name })
            { "Unknown key $it present in schema ${schema.name}" }
        }
        // validate attributes based on the event triggered
        schema.attributes.filter { attribute ->
            permissionCommand?.let { permissionCommand ->
                attribute.associatedEvents?.map { it.triggeringCommand }?.contains(permissionCommand)
            } ?: true
        }.forEach {
            it.validate(kvPairs[it.name])
        }
    }
}

