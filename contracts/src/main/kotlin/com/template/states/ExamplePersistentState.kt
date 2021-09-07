package com.template.states

import com.r3.utils.ExampleContract
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



@BelongsToContract(ExampleContract::class)
data class EncapsulatedState(
    val enclosingValue: String,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is ExampleSchemaV1) {
            return ExampleSchemaV1.EncapsulatedSchema(enclosingValue, linearId.id)
        }else{
            throw IllegalArgumentException("Unsupported Schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ExampleSchemaV1)

}



// model the encapsulating state as an independent state with a linear pointer to the encapsulated state.
// This type of modelling allows the encapsulated state to evolve independently.
@BelongsToContract(ExampleContract::class)
data class EncapsulatingState(
    val value: String,
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    val encapsulatedStateIdentifier : LinearPointer<EncapsulatedState>,
    override val participants: List<AbstractParty>
) : QueryableState, LinearState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is ExampleSchemaV1) {
            return ExampleSchemaV1.EncapsulatingSchema(
                identifier = linearId.id,
                encapsulatingValue = value,
                encapsulatedSchemaId = encapsulatedStateIdentifier.pointer.id)
        }else{
            throw IllegalArgumentException("Unsupported Schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ExampleSchemaV1)

}


// =========================================================================================================
//                      JPA SCHEMA definitions
// =========================================================================================================

object ExampleSchema

object ExampleSchemaV1 : MappedSchema(
    schemaFamily = ExampleSchema.javaClass,
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