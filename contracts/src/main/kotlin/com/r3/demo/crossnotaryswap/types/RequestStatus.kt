package com.r3.demo.crossnotaryswap.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class RequestStatus {
    REQUESTED,
    APPROVED,
    ABORTED,
    DENIED,
    FULFILLED
}


@CordaSerializable
enum class AssetRequestType {
    FUNGIBLE_ASSET_REQUEST,
    NON_FUNGIBLE_ASSET_REQUEST
}
