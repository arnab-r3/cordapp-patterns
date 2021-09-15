package com.r3.demo.datadistribution.flows

import net.corda.bn.states.BNPermission
import net.corda.bn.states.BNRole
import net.corda.core.serialization.CordaSerializable

const val DEFAULT_NOTARY = "O=Notary,L=London,C=GB"
const val BNO_PARTY = "O=PartyA,L=London,C=GB"

const val MAX_THREAD_SIZE = 20

@CordaSerializable
enum class DataAdminPermission : BNPermission {
    CAN_MANAGE_DATA
}

// permission required to alter data, anyone in the group can distribute data
@CordaSerializable
class DataAdminRole : BNRole("DataAdmin", DataAdminPermission.values().toSet())