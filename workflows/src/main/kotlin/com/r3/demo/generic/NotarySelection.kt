package com.r3.demo.generic

import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService



@CordaService
class NotaryUtility(private val appServiceHub: AppServiceHub) {

    fun <T:ContractState> getNotaryForState(state: Class<T>) : Party {
        return appServiceHub.networkMapCache.notaryIdentities[0]
    }
}