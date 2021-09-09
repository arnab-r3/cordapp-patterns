package com.r3.demo.stateencapsulation.contracts

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table



// =========================================================================================================
//                      Contract State definitions
// =========================================================================================================



@BelongsToContract(StateEncapsulationContract::class)
data class EncapsulatedState(
    val innerValue: String,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is EncapsulationSchemaV1 -> EncapsulationSchemaV1.EncapsulatedSchema(innerValue, linearId.id)
            else -> throw IllegalArgumentException("Unsupported Schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(EncapsulationSchemaV1)

    fun withNewValue(newValue: String) =
        EncapsulatedState(newValue, participants.toList(), linearId.copy())
}



// model the encapsulating state as an independent state with a linear pointer to the encapsulated state.
// This type of modelling allows the encapsulated state to evolve independently.
@BelongsToContract(StateEncapsulationContract::class)
data class EncapsulatingState(
    val outerValue: String,
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    val encapsulatedStateIdentifier : LinearPointer<EncapsulatedState>,
    override val participants: List<AbstractParty>
) : QueryableState, LinearState {


    constructor(outerValue: String, innerIdentifer: UUID, participants: List<AbstractParty>) : this (
        outerValue = outerValue,
        encapsulatedStateIdentifier =
            LinearPointer(UniqueIdentifier(id = innerIdentifer), EncapsulatedState::class.java),
        participants = participants
    )


    override fun generateMappedObject(schema: MappedSchema): PersistentState {

        return when (schema) {
            is EncapsulationSchemaV1 -> EncapsulationSchemaV1.EncapsulatingSchema(
                identifier = linearId.id,
                encapsulatingValue = outerValue,
                encapsulatedSchemaId = encapsulatedStateIdentifier.pointer.id)
            else -> throw IllegalArgumentException("Unsupported Schema")
        }

    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(EncapsulationSchemaV1)

    fun withNewValues(
        newValue:String = outerValue,
        innerIdentifer:UUID = encapsulatedStateIdentifier.pointer.id
    ) = EncapsulatingState(
        newValue,
        linearId.copy(),
        LinearPointer(UniqueIdentifier(id = innerIdentifer), EncapsulatedState::class.java),
        participants.toList())

}


// =========================================================================================================
//                      JPA SCHEMA definitions
// =========================================================================================================

object EncapsulationSchema

object EncapsulationSchemaV1 : MappedSchema(
    schemaFamily = EncapsulationSchema.javaClass,
    version = 1,
    mappedTypes = listOf(EncapsulatedSchema::class.java, EncapsulatingSchema::class.java)
) {
    override val migrationResource: String?
        get() = "example.changelog-master"

    @Entity
    @Table(name = "ENCAPSULATED")
    class EncapsulatedSchema(

        @Column(name="value")
        val enclosingValue: String,

        @Column(name="id")
        @Type(type = "uuid-char")
        val identifier: UUID
    ): PersistentState(), Serializable


    @Entity
    @Table(name="ENCAPSULATING")
    class EncapsulatingSchema(


        @Column(name="id")
        @Type(type = "uuid-char")
        val identifier: UUID,

        @Column(name="enclosing_value")
        val encapsulatingValue: String,

        @Column(name="encapsulated_id")
        @Type(type = "uuid-char")
        val encapsulatedSchemaId : UUID

    ) : PersistentState(), Serializable

}

data class User(val name: String, val age: Int) {
    constructor(name: String): this(name, -1) {  }
}