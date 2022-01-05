package com.r3.demo.crossnotaryswap.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class RequestStatus {
    REQUESTED,
    APPROVED,
    DENIED
}
