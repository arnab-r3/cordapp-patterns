package com.template.states

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.io.Serializable
import java.math.BigDecimal
import java.util.*
import javax.persistence.*


object DealSchema

object DealSchemaV1 : MappedSchema(
    schemaFamily = DealSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentDeal::class.java, PersistentProduct::class.java)
) {


    override val migrationResource: String
        get() = "deal-changelog";

    @Entity
    @Table(name="PRODUCT")
    class PersistentProduct(
        @Id
        @Column(name = "id")
        @Type(type = "uuid-char")
        val identifier: UUID,

        @Column(name = "name")
        val name: String,

        @Column(name = "price")
        val price: BigDecimal

    ) {
        constructor() : this(UUID.randomUUID(), "", BigDecimal.ZERO)
    }

    @Entity
    @Table(name = "DEAL")
    class PersistentDeal(

        @Column(name = "id")
        @Type(type = "uuid-char")
        val identifier: UUID,

        @OneToMany(
            cascade = [CascadeType.PERSIST]
        )
        val products: List<PersistentProduct> = mutableListOf()

    ) : PersistentState(), Serializable {
        constructor() : this(UUID.randomUUID(), emptyList())
    }
}