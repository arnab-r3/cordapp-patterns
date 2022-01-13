package com.r3.demo.crossnotaryswap.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger

@CordaService
class ExchangeRequestService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        val logger = contextLogger()
    }
}