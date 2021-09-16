package com.template.contracts

import net.corda.bn.states.BNPermission
import net.corda.bn.states.BNRole
import net.corda.core.serialization.CordaSerializable


@CordaSerializable
enum class DataAdminPermission : BNPermission {
    CAN_MANAGE_DATA
}

// permission required to alter data, anyone in the group can distribute data
@CordaSerializable
class DataAdminRole : BNRole("DataAdmin", DataAdminPermission.values().toSet())