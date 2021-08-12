package com.template.states

import com.template.contracts.TemplateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.util.*

@CordaSerializable
data class ProductState(
    val identifier: UUID,
    val name: String,
    val price: BigDecimal
)

@BelongsToContract(TemplateContract::class)
data class DealState (
    val products : List<ProductState>,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier
) : QueryableState, LinearState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        require(schema is DealSchemaV1) {"schema should be of type DealSchema"}
        val prods : MutableList<DealSchemaV1.PersistentProduct> = mutableListOf()

        for (product in products){
            prods += DealSchemaV1.PersistentProduct(
                product.identifier,
                product.name,
                product.price)

        }

        return DealSchemaV1.PersistentDeal(linearId.id, prods)

    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DealSchemaV1)
}
