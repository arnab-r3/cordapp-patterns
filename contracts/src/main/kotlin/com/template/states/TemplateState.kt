package com.template.states

import com.template.contracts.TemplateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

// *********
// * State *
// *********
@BelongsToContract(TemplateContract::class)
data class TemplateState(val msg: String,
                         val sender: Party,
                         val receiver: Party,
                         override val participants: List<AbstractParty> = listOf(sender,receiver)
) : ContractState

data class Tenant(val publicKey: PublicKey, override val participants: List<AbstractParty>) : AbstractParty(publicKey), QueryableState {

    override fun nameOrNull(): CordaX500Name? = null

    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)

    override fun toString() = "Tenant(${owningKey.toStringShort()})"

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        TODO("Not yet implemented")
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        TODO("Not yet implemented")
    }

}