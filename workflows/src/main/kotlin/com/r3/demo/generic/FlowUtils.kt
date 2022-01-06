package com.r3.demo.generic

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.ourIdentity
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.utilities.firstNotary
import com.r3.demo.crossnotaryswap.flows.utils.TokenRegistry
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
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
fun FlowLogic<*>.getPreferredNotaryForToken(tokenType: TokenType, backupSelector: (ServiceHub) -> Party = firstNotary()): Party {
    if (tokenType.isCustomTokenType()) argFail("Notary selection for custom token type not yet supported")

    val currencyCode = if(tokenType.isPointer())
        TokenRegistry.getTokenIdentifier(tokenType.tokenClass)
    else
        tokenType.tokenIdentifier

    val notaryString = try {
        val config: CordappConfig = serviceHub.getAppContext().config

        val key = "${currencyCode.toLowerCase()}_notary"
        config.getString(key)
    } catch (e: CordappConfigException) {
        ""
    }
    return if (notaryString.isBlank()) {
        backupSelector(serviceHub)
    } else {
        val notaryX500Name = CordaX500Name.parse(notaryString)
        val notaryParty = serviceHub.networkMapCache.getNotary(notaryX500Name)
            ?: throw IllegalStateException("Notary with name \"$notaryX500Name\" cannot be found in the network " +
                    "map cache. Either the notary does not exist, or there is an error in the config.")
        notaryParty
    }
}


@Suspendable
fun ServiceHub.stateObservers(state: EvolvableTokenType) : List<Party> {
    val observers = state.participants- state.maintainers - ourIdentity
    return observers.map { identityService.wellKnownPartyFromAnonymous(it)!! }
}

@Suspendable
fun firstNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.firstOrNull()
        ?: throw IllegalArgumentException("No available notaries.")
}
