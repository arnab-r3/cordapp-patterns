package com.r3.demo.accesscontrol

import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction


@CordaSerializable
interface AccessControllable<T : AccessControlState> {
    val accessControlStatePointer: LinearPointer<T>
}

@CordaSerializable
enum class Effect {
    DENY,
    ALLOW
}

@CordaSerializable
data class AccessControlPermission(
    val action: String,
    val effect: Effect
)

@CordaSerializable
abstract class AccessControlState(
    override val linearId: UniqueIdentifier,
    open val name: String,
    open val permissions: Set<AccessControlPermission>,
    open val maintainers: Set<Party>,
    override val participants: List<AbstractParty>
) : LinearState {

    constructor(
        name: String,
        participants: List<AbstractParty>,
        maintainers: Set<Party>,
        permissions: Set<AccessControlPermission> = emptySet()
    ) : this(
        linearId = UniqueIdentifier(),
        name = name,
        permissions = permissions,
        maintainers = maintainers,
        participants = participants)


    abstract fun members(ledgerTransaction: LedgerTransaction? = null): Set<AbstractParty>
    abstract fun members(serviceHub: ServiceHub? = null): Set<AbstractParty>
}

@CordaSerializable
@BelongsToContract(AccessControlContract::class)
data class MembershipBoundAccessControlState(
    override val name: String,
    override val permissions: Set<AccessControlPermission> = emptySet(),
    override val maintainers: Set<Party>,
    override val participants: List<AbstractParty> = maintainers.toList(),
    val membershipStatePointer: LinearPointer<MembershipState>
) : AccessControlState(name, participants, maintainers, permissions) {


    override fun members(ledgerTransaction: LedgerTransaction?): Set<AbstractParty> =
        ledgerTransaction?.let { membershipStatePointer.resolve(it).state.data.participants.toSet() }
            ?: throw IllegalArgumentException("Require a parameter of ledger transaction or serviceHub")

    override fun members(serviceHub: ServiceHub?): Set<AbstractParty> =
        serviceHub?.let { membershipStatePointer.resolve(it).state.data.participants.toSet() }
            ?: throw IllegalArgumentException("Require a parameter of ledger transaction or serviceHub")
}

@CordaSerializable
@BelongsToContract(AccessControlContract::class)
data class GroupBoundAccessControlState(
    override val name: String,
    override val permissions: Set<AccessControlPermission> = emptySet(),
    override val maintainers: Set<Party>,
    override val participants: List<AbstractParty> = maintainers.toList(),
    val groupStatePointer: LinearPointer<GroupState>
) : AccessControlState(name, participants, maintainers, permissions) {

    override fun members(ledgerTransaction: LedgerTransaction?): Set<AbstractParty> =
        ledgerTransaction?.let { groupStatePointer.resolve(it).state.data.participants.toSet() }
            ?: throw IllegalArgumentException("Require a parameter of ledger transaction or serviceHub")

    override fun members(serviceHub: ServiceHub?): Set<AbstractParty> =
        serviceHub?.let { groupStatePointer.resolve(it).state.data.participants.toSet() }
            ?: throw IllegalArgumentException("Require a parameter of ledger transaction or serviceHub")
}

@CordaSerializable
@BelongsToContract(AccessControlContract::class)
data class PartyAccessControlState(
    override val name: String,
    override val permissions: Set<AccessControlPermission>,
    override val maintainers: Set<Party>,
    val members: Set<AbstractParty>,
    override val participants: List<AbstractParty> = (maintainers + members).toList()
) : AccessControlState(name, participants, maintainers, permissions) {

    override fun members(ledgerTransaction: LedgerTransaction?): Set<AbstractParty> = members

    override fun members(serviceHub: ServiceHub?): Set<AbstractParty> = members
}