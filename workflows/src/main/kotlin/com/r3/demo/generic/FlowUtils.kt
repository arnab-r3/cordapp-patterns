package com.r3.demo.generic

import net.corda.bn.flows.MembershipNotFoundException
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.node.ServiceHub

fun getDefaultNotary(serviceHub: ServiceHub) = serviceHub.networkMapCache.notaryIdentities.first()

fun argFail(message: String) : Nothing = throw IllegalArgumentException(message)
fun flowFail(message: String) : Nothing = throw FlowException(message)
fun authFail(message: String) : Nothing = throw MembershipNotFoundException(message)

fun <T : LinearState> linearPointer(id: String, clazz: Class<T>) = LinearPointer(UniqueIdentifier.fromString(id),clazz)