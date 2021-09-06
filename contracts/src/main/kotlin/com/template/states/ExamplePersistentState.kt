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
import javax.persistence.Id
import javax.persistence.Table



// =========================================================================================================
//                      Contract State definitions
// =========================================================================================================


data class EncapsulatedState(
    val enclosingValue: String,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState



// model the encapsulating state as an independent state with a linear pointer to the encapsulated state.
// This type of modelling allows the encapsulated state to evolve independently.
@BelongsToContract(ExampleContract::class)
data class EncapsulatingState(
    val value: String,
    val identifier: UniqueIdentifier = UniqueIdentifier(),
    val encapsulatedStateIdentifier : LinearPointer<EncapsulatedState>,
    override val participants: List<AbstractParty>
) : QueryableState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is ExampleSchemaV1) {
            return ExampleSchemaV1.EncapsulatingSchema(
                identifier = identifier.id,
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
    mappedTypes = listOf()
) {
    override val migrationResource: String?
        get() = "super.migrationResource"

    @Entity
    @Table(name = "ENCAPSULATED")
    class EncapsulatedSchema(

        @Column(name="value")
        val enclosingValue: String,

        @Id
        @Column(name="id")
        @Type(type = "uuid-char")
        val identifier: UUID
    )


    @Entity
    @Table(name="ENCAPSULATING")
    class EncapsulatingSchema(


        @Column(name="id")
        @Id
        @Type(type = "uuid-char")
        val identifier: UUID,

        @Column(name="enclosing_value")
        val encapsulatingValue: String,

        @Column(name="encapsulated_id")
        @Type(type = "uuid-char")
        val encapsulatedSchemaId : UUID

    ) : PersistentState(), Serializable

}