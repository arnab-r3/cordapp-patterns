package com.r3.demo.common

import net.corda.bn.states.BNPermission
import net.corda.bn.states.BNRole
import net.corda.bn.states.MembershipState
import net.corda.core.serialization.CordaSerializable


@CordaSerializable
enum class DataAdminPermission : BNPermission {
    CAN_MANAGE_DATA,
    CAN_DISTRIBUTE_DATA
}

// permission required to alter data, anyone in the group can distribute data
@CordaSerializable
class DataAdminRole : BNRole("DataAdmin", DataAdminPermission.values().toSet())

@CordaSerializable
class NetworkMemberRole: BNRole("NetworkMemberRole", setOf(DataAdminPermission.CAN_DISTRIBUTE_DATA))


fun MembershipState.canManageData() = DataAdminPermission.CAN_MANAGE_DATA in permissions()
fun MembershipState.canDistributeData() = DataAdminPermission.CAN_DISTRIBUTE_DATA in permissions()
