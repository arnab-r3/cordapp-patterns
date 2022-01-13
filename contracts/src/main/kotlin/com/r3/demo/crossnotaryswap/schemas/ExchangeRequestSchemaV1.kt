package com.r3.demo.crossnotaryswap.schemas

import com.r3.demo.crossnotaryswap.types.AssetRequestType
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.util.*
import javax.persistence.*

object ExchangeRequestSchema

object ExchangeRequestSchemaV1 : MappedSchema(
    schemaFamily = ExchangeRequestSchema::class.java,
    version = 1,
    mappedTypes = listOf(ExchangeRequest::class.java)
)

@Entity
@CordaSerializable
class ExchangeRequest(

    @Id
    @Column(name = "request_id", nullable = false)
    var requestId: String = UUID.randomUUID().toString(),

    @Column(name = "buyer", nullable = false)
    var buyer: AbstractParty,

    @Column(name = "seller", nullable = false)
    var seller: AbstractParty,

    @Column(name = "buyer_asset_id", nullable = false)
    var buyerAssetTokenIdentifier: String,

    @Column(name = "seller_asset_id", nullable = false)
    var sellerAssetTokenIdentifier: String,

    @Column(name = "buyer_asset_qty", nullable = true)
    var buyerAssetQty: BigDecimal? = null,

    @Column(name = "seller_asset_qty", nullable = true)
    var sellerAssetQty: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_asset_request_type", nullable = false)
    var buyerAssetRequestType: AssetRequestType,

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_asset_request_type", nullable = false)
    var sellerAssetRequestType: AssetRequestType,

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = true, length = 12)
    var requestStatus: RequestStatus? = null,

    @Column(name = "reason", nullable = true)
    var reason: String? = null,

    @Column(name = "tx_id", nullable = true)
    var txId: String? = null,

    @Lob
    @Column(name = "transaction", nullable = true)
    var unsignedTransaction: ByteArray? = null

)

