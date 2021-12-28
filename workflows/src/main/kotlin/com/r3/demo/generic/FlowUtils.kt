package com.r3.demo.generic

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.utilities.firstNotary
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub

fun getDefaultNotary(serviceHub: ServiceHub) = serviceHub.networkMapCache.notaryIdentities.first()

fun argFail(message: String): Nothing = throw IllegalArgumentException(message)
fun flowFail(message: String): Nothing = throw FlowException(message)
fun authFail(message: String): Nothing = throw MembershipNotFoundException(message)

fun <T : LinearState> linearPointer(id: String, clazz: Class<T>) = LinearPointer(UniqueIdentifier.fromString(id), clazz)

//inline fun <reified T: ContractState> staticPointer(stateAndRef: StateAndRef<T>) = StatePointer.staticPointer(stateAndRef)


@Suspendable
@JvmOverloads
fun getPreferredNotaryForToken(services: ServiceHub, tokenType: String, backupSelector: (ServiceHub) -> Party = firstNotary()): Party {
    val notaryString = try {
        val config: CordappConfig = services.getAppContext().config
        config.getString("${tokenType.toLowerCase()}.notary")
    } catch (e: CordappConfigException) {
        ""
    }
    return if (notaryString.isBlank()) {
        backupSelector(services)
    } else {
        val notaryX500Name = CordaX500Name.parse(notaryString)
        val notaryParty = services.networkMapCache.getNotary(notaryX500Name)
            ?: throw IllegalStateException("Notary with name \"$notaryX500Name\" cannot be found in the network " +
                    "map cache. Either the notary does not exist, or there is an error in the config.")
        notaryParty
    }
}

@Suspendable
fun firstNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.firstOrNull()
        ?: throw IllegalArgumentException("No available notaries.")
}
