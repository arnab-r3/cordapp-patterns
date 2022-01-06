package com.r3.demo.crossnotaryswap.schemas

import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import java.util.*
import javax.persistence.*

object ExchangeRequestSchema

class ExchangeRequestSchemaV1: MappedSchema(
    schemaFamily = ExchangeRequestSchema::class.java,
    version = 1,
    mappedTypes = listOf(ExchangeRequest::class.java)
)

@Entity
data class ExchangeRequest(

    @Id
    @Column(name = "request_id", nullable = false)
    var requestId: String = UUID.randomUUID().toString(),

    @Column(name = "buyer", nullable = false)
    var buyer: AbstractParty,

    @Column(name = "seller", nullable = false)
    var seller: AbstractParty,

    @Column(name = "buyer_asset_type", nullable = false)
    var buyerAssetType: String,

    @Column(name = "seller_asset_type", nullable = false)
    var sellerAssetType: String,

    @Column(name = "buyer_asset_qty", nullable = true)
    var buyerAssetQty: Long?,

    @Column(name = "seller_asset_qty", nullable = true)
    var sellerAssetQty: Long?,

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = true, length = 12)
    var requestStatus: RequestStatus? = null,

    @Column(name="reason", nullable = true)
    var reason: String? = null,

    @Column(name = "tx_id", nullable = true)
    var txId: String? = null
)